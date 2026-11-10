package com.bhive.receipt.service;

import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReceiptDeliveryPreferenceServiceTest {

    @Mock
    private CustomerProfileRepository profileRepository;

    @Test
    void enablesBothReceiptChannelsOnNewCustomerProfilesByDefault() {
        CustomerProfile profile = new CustomerProfile();

        assertTrue(profile.isReceiptEmailEnabled());
        assertTrue(profile.isReceiptPhoneEnabled());
    }

    @Test
    void enablesSelectedProfileChannelsAndNormalizesPhoneForMatching() {
        CustomerProfile profile = customerProfile(88L);
        when(profileRepository.findByUserIdAndTenantIdForUpdate(88L, 7L)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, true);

        var preference = service.updatePreference(88L, 7L, true, true);

        assertTrue(preference.contactMatchingEnabled());
        assertTrue(preference.emailEnabled());
        assertTrue(preference.phoneEnabled());
        assertTrue(preference.emailAvailable());
        assertTrue(preference.phoneAvailable());
        assertEquals("919876543210", profile.getPhoneNormalized());
        assertEquals("customer@example.com", profile.getEmailNormalized());
        verify(profileRepository).save(profile);
    }

    @Test
    void doesNotAllowOptInWhenUnverifiedContactMatchingIsDisabled() {
        CustomerProfile profile = customerProfile(88L);
        when(profileRepository.findByUserIdAndTenantIdForUpdate(88L, 7L)).thenReturn(Optional.of(profile));
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, false);

        assertThrows(ResponseStatusException.class, () -> service.updatePreference(88L, 7L, true, false));
    }

    @Test
    void rejectsContactMatchingWhenUnverifiedMatchingIsDisabled() {
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, false);

        ResponseStatusException emailException = assertThrows(
            ResponseStatusException.class,
            () -> service.findOptedInRecipientsByEmail("customer@example.com")
        );
        ResponseStatusException phoneException = assertThrows(
            ResponseStatusException.class,
            () -> service.findOptedInRecipientsByPhone("+91 98765-43210")
        );

        assertEquals(409, emailException.getStatusCode().value());
        assertEquals(409, phoneException.getStatusCode().value());
    }

    @Test
    void rejectsPhoneOptInWhenNoPhoneIsOnTheProfile() {
        CustomerProfile profile = customerProfile(88L);
        profile.setPhone(null);
        when(profileRepository.findByUserIdAndTenantIdForUpdate(88L, 7L)).thenReturn(Optional.of(profile));
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, true);

        assertThrows(ResponseStatusException.class, () -> service.updatePreference(88L, 7L, false, true));
        assertTrue(profile.isReceiptPhoneEnabled());
    }

    @Test
    void resolvesOnlyOptedInProfileContacts() {
        CustomerProfile profile = customerProfile(88L);
        profile.setReceiptEmailEnabled(true);
        profile.setReceiptPhoneEnabled(true);
        when(profileRepository.findByEmailNormalizedAndReceiptEmailEnabledTrueAndUserIdIsNotNull("customer@example.com"))
            .thenReturn(List.of(profile));
        when(profileRepository.findByPhoneNormalizedAndReceiptPhoneEnabledTrueAndUserIdIsNotNull("919876543210"))
            .thenReturn(List.of(profile));
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, true);

        assertEquals(List.of(new ReceiptRecipient(88L, 7L)),
            service.findOptedInRecipientsByEmail(" CUSTOMER@example.com "));
        assertEquals(List.of(new ReceiptRecipient(88L, 7L)),
            service.findOptedInRecipientsByPhone("+91 98765-43210"));
    }

    @Test
    void doesNotSelectAnAmbiguousPhoneMatch() {
        CustomerProfile first = customerProfile(88L);
        CustomerProfile second = customerProfile(89L);
        when(profileRepository.findByPhoneNormalizedAndReceiptPhoneEnabledTrueAndUserIdIsNotNull("919876543210"))
            .thenReturn(List.of(first, second));
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, true);

        assertTrue(service.findOptedInRecipientsByPhone("+91 98765-43210").isEmpty());
    }

    @Test
    void findsTenantContextsForLegacyReceiptLinkTokenRecipients() {
        CustomerProfile first = customerProfile(88L);
        CustomerProfile second = customerProfile(88L);
        second.setTenantId(9L);
        when(profileRepository.findByUserIdIn(List.of(88L))).thenReturn(List.of(first, second));
        ReceiptDeliveryPreferenceService service = new ReceiptDeliveryPreferenceService(profileRepository, true);

        assertEquals(
            List.of(new ReceiptRecipient(88L, 7L), new ReceiptRecipient(88L, 9L)),
            service.findRecipientsByUserId(88L)
        );
    }

    private CustomerProfile customerProfile(Long userId) {
        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(userId);
        profile.setTenantId(7L);
        profile.setEmail("customer@example.com");
        profile.setPhone("+91 98765-43210");
        return profile;
    }
}
