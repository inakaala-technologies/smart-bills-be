package com.bhive.bills.service;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedBillPersistenceServiceTest {

    @Mock
    private SavedBillRepository savedBillRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private BillProviderRepository providerRepository;
    @InjectMocks
    private SavedBillPersistenceService persistenceService;

    private final ConsumerIdentityResolver identityResolver = new ConsumerIdentityResolver();

    @Test
    void inserts_new_saved_bill_for_customer_and_provider_identity() {
        CustomerProfile customer = customer(31L);
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        when(customerProfileRepository.findById(31L)).thenReturn(Optional.of(customer));
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(savedBillRepository.findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                31L, "KSEB", identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")).hash()
        )).thenReturn(Optional.empty());
        when(savedBillRepository.saveAndFlush(any(SavedBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SavedBill result = persistenceService.upsert(
                31L,
                "KSEB",
                identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")),
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"bill\":\"one\"}",
                null,
                null
        );

        assertEquals(customer, result.getCustomer());
        assertEquals(provider, result.getProvider());
        assertEquals("K-1", result.getConsumerNumber());
        assertEquals("JSON", result.getResponseFormat());
        verify(savedBillRepository).saveAndFlush(result);
    }

    @Test
    void inserts_new_water_bill_with_water_category() {
        CustomerProfile customer = customer(31L);
        BillProvider provider = provider("KIADB", "WATER");
        ConsumerIdentity identity = identityResolver.resolve("KIADB", Map.of("RegNum", "KIADB2019AL0001"));
        when(customerProfileRepository.findById(31L)).thenReturn(Optional.of(customer));
        when(providerRepository.findByCodeAndActiveTrue("KIADB")).thenReturn(Optional.of(provider));
        when(savedBillRepository.findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                31L, "KIADB", identity.hash()
        )).thenReturn(Optional.empty());
        when(savedBillRepository.saveAndFlush(any(SavedBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SavedBill result = persistenceService.upsert(
                31L,
                "KIADB",
                identity,
                "{\"RegNum\":\"KIADB2019AL0001\"}",
                "XML",
                "application/xml",
                null,
                "<Bill/>",
                null
        );

        assertEquals("WATER", result.getCategory().getCode());
        assertEquals("<Bill/>", result.getResponseXml());
    }

    @Test
    void updates_existing_customer_provider_consumer_record() {
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")).hash()
        );
        when(savedBillRepository.findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                31L, "KSEB", existing.getConsumerIdentityHash()
        )).thenReturn(Optional.of(existing));
        when(savedBillRepository.saveAndFlush(existing)).thenReturn(existing);

        SavedBill result = persistenceService.upsert(
                31L,
                "KSEB",
                identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")),
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"bill\":\"updated\"}",
                null,
                null
        );

        assertSame(existing, result);
        assertEquals("{\"bill\":\"updated\"}", result.getResponseJson());
        verify(customerProfileRepository, never()).findById(any());
        verify(providerRepository, never()).findByCodeAndActiveTrue(any());
    }

    @Test
    void different_identity_creates_distinct_records_for_consumer_or_provider_changes() {
        CustomerProfile customer = customer(31L);
        BillProvider kseb = provider("KSEB", "ELECTRICITY");
        BillProvider uppcl = provider("UPPCL", "ELECTRICITY");
        when(customerProfileRepository.findById(31L)).thenReturn(Optional.of(customer));
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(kseb));
        when(providerRepository.findByCodeAndActiveTrue("UPPCL")).thenReturn(Optional.of(uppcl));
        when(savedBillRepository.findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                any(), any(), any()
        )).thenReturn(Optional.empty());
        when(savedBillRepository.saveAndFlush(any(SavedBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.upsert(
                31L, "KSEB", identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")),
                "{}", "JSON", "application/json", "{}", null, null
        );
        persistenceService.upsert(
                31L, "KSEB", identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-2")),
                "{}", "JSON", "application/json", "{}", null, null
        );
        persistenceService.upsert(
                31L, "UPPCL", identityResolver.resolve(
                        "UPPCL", Map.of("AccountNo", "A-1", "Discom", "D-1")
                ),
                "{}", "JSON", "application/json", "{}", null, null
        );

        ArgumentCaptor<SavedBill> captor = ArgumentCaptor.forClass(SavedBill.class);
        verify(savedBillRepository, org.mockito.Mockito.times(3)).saveAndFlush(captor.capture());
        assertEquals("K-1", captor.getAllValues().get(0).getConsumerNumber());
        assertEquals("K-2", captor.getAllValues().get(1).getConsumerNumber());
        assertEquals("A-1", captor.getAllValues().get(2).getConsumerNumber());
    }

    @Test
    void table_declares_database_unique_business_key() {
        jakarta.persistence.Table table = SavedBill.class.getAnnotation(jakarta.persistence.Table.class);
        assertTrue(java.util.Arrays.stream(table.uniqueConstraints())
                .anyMatch(constraint -> java.util.List.of(constraint.columnNames()).equals(
                        java.util.List.of("customer_id", "provider_id", "consumer_identity_hash")
                )));
    }

    private CustomerProfile customer(Long id) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(id);
        return customer;
    }

    private BillProvider provider(String code, String categoryCode) {
        return new BillProvider(code, code, new BillCategory(categoryCode, categoryCode));
    }
}
