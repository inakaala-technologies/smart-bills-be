package com.bhive.bills.service;

import com.bhive.bills.dto.RecordCustomerBillConsentRequest;
import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.CustomerBillConsent;
import com.bhive.bills.entity.ProviderInputField;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.CustomerBillConsentRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerBillConsentServiceTest {

    @Mock
    private CustomerBillConsentRepository consentRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private BillProviderRepository providerRepository;
    @Mock
    private ProviderInputFieldRepository inputFieldRepository;
    @Mock
    private SavedBillRepository savedBillRepository;
    @Mock
    private SavedBillService savedBillService;

    private CustomerBillConsentService service;
    private BillProvider provider;

    @BeforeEach
    void setUp() {
        service = new CustomerBillConsentService(
                consentRepository,
                customerProfileRepository,
                providerRepository,
                inputFieldRepository,
                savedBillRepository,
                savedBillService,
                new ObjectMapper()
        );
        provider = new BillProvider("KSEB", "Kerala State Electricity Board",
                new BillCategory("ELECTRICITY", "Electricity"));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void records_acceptance_with_server_generated_audit_fields_and_field_names_only() {
        authenticate();
        configureProvider();
        when(consentRepository.saveAndFlush(any(CustomerBillConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.recordAcceptance(request(
                "KSEB", List.of("ConsumerNo"), ApplicationConsentStatement.VERSION, true
        ));

        assertNotNull(response.consentRecordId());
        assertEquals("KSEB", response.providerCode());
        assertEquals(List.of("ConsumerNo"), response.requestedDataFields());
        assertEquals(ApplicationConsentStatement.PURPOSE_CODE, response.purposeCode());
        assertEquals(ApplicationConsentStatement.VERSION, response.statementVersion());
        assertEquals(64, response.statementHash().length());
        assertEquals("ACCEPTED", response.status());
        assertNotNull(response.acceptedAt());
        assertNull(response.revokedAt());
        assertNull(response.savedBillId());
        verify(consentRepository).saveAndFlush(any(CustomerBillConsent.class));
    }

    @Test
    void hashes_the_exact_published_statement_as_utf8_without_normalization() throws Exception {
        authenticate();
        configureProvider();
        when(consentRepository.saveAndFlush(any(CustomerBillConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String statement = ApplicationConsentStatement.TEXT;
        assertEquals(statement, statement.strip());
        assertFalse(statement.contains("\r"));
        String expectedDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(statement.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("ca50a9060f756c8a73a2eda498bd0cdc6624b0545674c008ab6019001ac813b1",
                expectedDigest);

        var response = service.recordAcceptance(request(
                "KSEB", List.of("ConsumerNo"), ApplicationConsentStatement.VERSION, true
        ));

        assertEquals(expectedDigest, response.statementHash());
    }

    @Test
    void rejects_unknown_or_inactive_provider() {
        authenticate();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(providerRepository.findByCodeAndActiveTrue("UNKNOWN")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.recordAcceptance(request(
                        "UNKNOWN", List.of("x"), ApplicationConsentStatement.VERSION, true
                ))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_requested_fields_not_configured_for_provider() {
        authenticate();
        configureProvider();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.recordAcceptance(request(
                        "KSEB", List.of("OtherField"), ApplicationConsentStatement.VERSION, true
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_incorrect_statement_version_or_missing_acceptance() {
        authenticate();

        ResponseStatusException versionException = assertThrows(
                ResponseStatusException.class,
                () -> service.recordAcceptance(request("KSEB", List.of("ConsumerNo"), "old", true))
        );
        ResponseStatusException acceptanceException = assertThrows(
                ResponseStatusException.class,
                () -> service.recordAcceptance(request(
                        "KSEB", List.of("ConsumerNo"), ApplicationConsentStatement.VERSION, false
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, versionException.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, acceptanceException.getStatusCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_statement_versions_with_whitespace_or_additional_characters() {
        authenticate();

        for (String invalidVersion : List.of(" 1", "1 ", "1.0", "01")) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.recordAcceptance(request(
                            "KSEB", List.of("ConsumerNo"), invalidVersion, true
                    ))
            );
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        verify(consentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_unauthenticated_access_without_looking_up_customer() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.recordAcceptance(request(
                        "KSEB", List.of("ConsumerNo"), ApplicationConsentStatement.VERSION, true
                ))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(customerProfileRepository, never()).findByUserIdAndTenantId(any(), any());
    }

    @Test
    void returns_only_history_for_authenticated_customer() {
        authenticate();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        Page<CustomerBillConsent> rows = new PageImpl<>(List.of());
        when(consentRepository.findAllByCustomer_Id(70L, PageRequest.of(0, 20)))
                .thenReturn(rows);

        Page<?> response = service.getHistory(null, null, PageRequest.of(0, 20));

        assertEquals(0, response.getTotalElements());
        verify(consentRepository).findAllByCustomer_Id(70L, PageRequest.of(0, 20));
    }

    @Test
    void revokes_owned_consent_and_preserves_audit_record() {
        authenticate();
        CustomerBillConsent consent = new CustomerBillConsent(
                customer(70L), provider, "[\"ConsumerNo\"]",
                ApplicationConsentStatement.PURPOSE_CODE,
                ApplicationConsentStatement.VERSION, "a".repeat(64),
                java.time.LocalDateTime.now(), null
        );
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id(
                consent.getConsentRecordId(), 70L
        )).thenReturn(Optional.of(consent));
        when(consentRepository.saveAndFlush(consent)).thenReturn(consent);

        var response = service.revoke(consent.getConsentRecordId());

        assertEquals("REVOKED", response.status());
        assertNotNull(response.revokedAt());
        verify(consentRepository).saveAndFlush(consent);
    }

    @Test
    void cannot_revoke_another_customers_consent() {
        authenticate();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id("other-id", 70L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.revoke("other-id")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(consentRepository, never()).saveAndFlush(any());
    }

    @Test
    void authorizes_matching_accepted_consent_and_links_it_to_the_prepared_bill() {
        authenticate();
        CustomerBillConsent consent = acceptedConsent();
        var savedBill = org.mockito.Mockito.mock(com.bhive.bills.entity.SavedBill.class);
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id(
                consent.getConsentRecordId(), 70L
        )).thenReturn(Optional.of(consent));
        when(savedBillService.prepareFetchAttempt("KSEB", java.util.Map.of("ConsumerNo", "123")))
                .thenReturn(501L);
        when(savedBillRepository.findByIdAndCustomer_Id(501L, 70L))
                .thenReturn(Optional.of(savedBill));
        when(consentRepository.saveAndFlush(consent)).thenReturn(consent);

        Long savedBillId = service.authorizeAndPrepareManualFetch(
                consent.getConsentRecordId(),
                70L,
                "KSEB",
                List.of("ConsumerNo"),
                java.util.Map.of("ConsumerNo", "123")
        );

        assertEquals(501L, savedBillId);
        assertEquals(savedBill, consent.getSavedBill());
        verify(savedBillService).prepareFetchAttempt("KSEB", java.util.Map.of("ConsumerNo", "123"));
        verify(consentRepository).saveAndFlush(consent);
    }

    @Test
    void rejects_missing_consent_before_preparing_manual_fetch() {
        authenticate();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id("missing", 70L))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.authorizeAndPrepareManualFetch(
                "missing", 70L, "KSEB", List.of("ConsumerNo"),
                java.util.Map.of("ConsumerNo", "123")
        ));

        verify(savedBillService, never()).prepareFetchAttempt(any(), any());
    }

    @Test
    void rejects_revoked_consent_before_preparing_manual_fetch() {
        authenticate();
        CustomerBillConsent consent = acceptedConsent();
        consent.revoke(java.time.LocalDateTime.now());
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id(
                consent.getConsentRecordId(), 70L
        )).thenReturn(Optional.of(consent));

        assertThrows(ResponseStatusException.class, () -> service.authorizeAndPrepareManualFetch(
                consent.getConsentRecordId(), 70L, "KSEB", List.of("ConsumerNo"),
                java.util.Map.of("ConsumerNo", "123")
        ));

        verify(savedBillService, never()).prepareFetchAttempt(any(), any());
    }

    @Test
    void rejects_reusing_an_application_consent_already_linked_to_a_saved_bill() {
        authenticate();
        CustomerBillConsent consent = acceptedConsent();
        consent.linkToSavedBill(org.mockito.Mockito.mock(
                com.bhive.bills.entity.SavedBill.class
        ));
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id(
                consent.getConsentRecordId(), 70L
        )).thenReturn(Optional.of(consent));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.authorizeAndPrepareManualFetch(
                        consent.getConsentRecordId(), 70L, "KSEB", List.of("ConsumerNo"),
                        java.util.Map.of("ConsumerNo", "123")
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(savedBillService, never()).prepareFetchAttempt(any(), any());
    }

    @Test
    void rejects_provider_or_requested_field_mismatch_before_preparing_manual_fetch() {
        authenticate();
        CustomerBillConsent consent = acceptedConsent();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(consentRepository.findByConsentRecordIdAndCustomer_Id(
                consent.getConsentRecordId(), 70L
        )).thenReturn(Optional.of(consent));

        assertThrows(ResponseStatusException.class, () -> service.authorizeAndPrepareManualFetch(
                consent.getConsentRecordId(), 70L, "OTHER", List.of("ConsumerNo"),
                java.util.Map.of("ConsumerNo", "123")
        ));
        assertThrows(ResponseStatusException.class, () -> service.authorizeAndPrepareManualFetch(
                consent.getConsentRecordId(), 70L, "KSEB", List.of("OtherField"),
                java.util.Map.of("OtherField", "123")
        ));

        verify(savedBillService, never()).prepareFetchAttempt(any(), any());
    }

    @Test
    void rejects_cross_customer_consent_before_preparing_manual_fetch() {
        authenticate();
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));

        assertThrows(ResponseStatusException.class, () -> service.authorizeAndPrepareManualFetch(
                "other-customer-consent", 71L, "KSEB", List.of("ConsumerNo"),
                java.util.Map.of("ConsumerNo", "123")
        ));

        verify(consentRepository, never()).findByConsentRecordIdAndCustomer_Id(any(), any());
        verify(savedBillService, never()).prepareFetchAttempt(any(), any());
    }

    private CustomerBillConsent acceptedConsent() {
        return new CustomerBillConsent(
                customer(70L), provider, "[\"ConsumerNo\"]",
                ApplicationConsentStatement.PURPOSE_CODE,
                ApplicationConsentStatement.VERSION, "a".repeat(64),
                java.time.LocalDateTime.now(), null
        );
    }

    private void configureProvider() {
        when(customerProfileRepository.findByUserIdAndTenantId(7L, 9L))
                .thenReturn(Optional.of(customer(70L)));
        when(providerRepository.findByCodeAndActiveTrue("KSEB"))
                .thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
    }

    private void authenticate() {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
    }

    private RecordCustomerBillConsentRequest request(
            String providerCode,
            List<String> fields,
            String statementVersion,
            boolean accepted
    ) {
        return new RecordCustomerBillConsentRequest(
                providerCode,
                fields,
                ApplicationConsentStatement.PURPOSE_CODE,
                statementVersion,
                accepted,
                null
        );
    }

    private CustomerProfile customer(Long id) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(id);
        customer.setUserId(7L);
        customer.setTenantId(9L);
        customer.setName("Test");
        return customer;
    }
}
