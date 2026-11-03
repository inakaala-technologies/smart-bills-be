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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void prepares_a_new_fetch_record_without_a_provider_response() {
        CustomerProfile customer = customer(31L);
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        ConsumerIdentity identity = identityResolver.resolve(
                "KSEB",
                Map.of("ConsumerNo", "K-1")
        );
        when(customerProfileRepository.findById(31L)).thenReturn(Optional.of(customer));
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(savedBillRepository.findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                31L, "KSEB", identity.hash()
        )).thenReturn(Optional.empty());
        when(savedBillRepository.saveAndFlush(any(SavedBill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SavedBill result = persistenceService.prepareFetchAttempt(
                31L,
                "KSEB",
                identity,
                "{\"ConsumerNo\":\"K-1\"}"
        );

        assertEquals("{\"ConsumerNo\":\"K-1\"}", result.getProviderInputData());
        assertEquals("IN_PROGRESS", result.getFetchStatus());
        assertNull(result.getResponseFormat());
        assertNull(result.getFetchedAt());
        verify(savedBillRepository).saveAndFlush(result);
    }

    @Test
    void manual_retry_recovers_only_a_stale_manual_attempt() {
        ConsumerIdentity identity = identityResolver.resolve(
                "KSEB",
                Map.of("ConsumerNo", "K-1")
        );
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                identity.hash()
        );
        existing.prepareFetch("{}", java.time.LocalDateTime.now().minusHours(1));
        when(savedBillRepository
                .findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        31L, "KSEB", identity.hash()
                )).thenReturn(Optional.of(existing));
        when(savedBillRepository.saveAndFlush(existing)).thenReturn(existing);

        persistenceService.prepareFetchAttempt(31L, "KSEB", identity, "{\"ConsumerNo\":\"K-1\"}");

        assertEquals("{\"ConsumerNo\":\"K-1\"}", existing.getProviderInputData());
        assertTrue(existing.getLastFetchAttemptAt().isAfter(
                java.time.LocalDateTime.now().minusMinutes(1)
        ));
        assertNull(existing.getFetchClaimToken());
    }

    @Test
    void manual_retry_cannot_take_over_a_stale_scheduler_claim() {
        ConsumerIdentity identity = identityResolver.resolve(
                "KSEB",
                Map.of("ConsumerNo", "K-1")
        );
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                identity.hash()
        );
        existing.prepareFetch("{}", java.time.LocalDateTime.now().minusHours(1));
        existing.claimFetch(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000051"),
                java.time.LocalDateTime.now().minusHours(1)
        );
        when(savedBillRepository
                .findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        31L, "KSEB", identity.hash()
                )).thenReturn(Optional.of(existing));

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> persistenceService.prepareFetchAttempt(
                        31L, "KSEB", identity, "{\"ConsumerNo\":\"K-1\"}"
                )
        );

        verify(savedBillRepository, never()).saveAndFlush(any(SavedBill.class));
    }

    @Test
    void failed_retry_keeps_the_previous_successful_response_and_timestamp() {
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")).hash()
        );
        existing.updateResponse(
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"bill\":\"previous-success\"}",
                null,
                null
        );
        java.time.LocalDateTime successfulFetchAt = existing.getFetchedAt();
        when(savedBillRepository
                .findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        31L, "KSEB", existing.getConsumerIdentityHash()
                )).thenReturn(Optional.of(existing));
        when(savedBillRepository.saveAndFlush(existing)).thenReturn(existing);

        persistenceService.prepareFetchAttempt(
                31L,
                "KSEB",
                identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")),
                "{\"ConsumerNo\":\"K-1\"}"
        );
        persistenceService.markFetchFailed(
                31L,
                "KSEB",
                existing.getConsumerIdentityHash(),
                "PROVIDER_HTTP_ERROR"
        );

        assertEquals("FAILED", existing.getFetchStatus());
        assertEquals("PROVIDER_HTTP_ERROR", existing.getLastFetchError());
        assertEquals("{\"bill\":\"previous-success\"}", existing.getResponseJson());
        assertEquals(successfulFetchAt, existing.getFetchedAt());
    }

    @Test
    void consent_deferral_preserves_success_and_does_not_consume_retry_attempts() {
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                "saved-hash"
        );
        existing.updateResponse(
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"bill\":\"previous-success\"}",
                null,
                null
        );
        existing.claimFetch(java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000051"
        ), java.time.LocalDateTime.now());
        java.time.LocalDateTime fetchedAt = existing.getFetchedAt();
        when(savedBillRepository.findByIdAndFetchClaimToken(
                51L,
                "00000000-0000-0000-0000-000000000051"
        )).thenReturn(Optional.of(existing));
        when(savedBillRepository.saveAndFlush(existing)).thenReturn(existing);

        persistenceService.deferScheduledFetch(
                new ScheduledBillFetch(
                        51L,
                        31L,
                        4L,
                        "KSEB",
                        "ELECTRICITY",
                        "{\"ConsumerNo\":\"K-1\"}",
                        "00000000-0000-0000-0000-000000000051"
                ),
                24
        );

        assertEquals("FAILED", existing.getFetchStatus());
        assertEquals("CONSENT_UNAVAILABLE", existing.getLastFetchError());
        assertEquals("{\"bill\":\"previous-success\"}", existing.getResponseJson());
        assertEquals(fetchedAt, existing.getFetchedAt());
        assertEquals(0, existing.getFetchRetryCount());
        assertTrue(existing.getNextFetchAt().isAfter(java.time.LocalDateTime.now()));
        assertNull(existing.getFetchClaimToken());
    }

    @Test
    void temporary_scheduled_failure_uses_exponential_retry_and_stops_at_limit() {
        SavedBill existing = new SavedBill(
                customer(31L),
                provider("KSEB", "ELECTRICITY"),
                new BillCategory("ELECTRICITY", "Electricity"),
                "K-1",
                "retry-hash"
        );
        existing.claimFetch(java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        ), java.time.LocalDateTime.now());
        when(savedBillRepository.findByIdAndFetchClaimToken(
                91L,
                "00000000-0000-0000-0000-000000000001"
        )).thenReturn(Optional.of(existing));
        when(savedBillRepository.saveAndFlush(existing)).thenReturn(existing);
        ScheduledBillFetch claim = new ScheduledBillFetch(
                91L,
                31L,
                4L,
                "KSEB",
                "ELECTRICITY",
                "{}",
                "00000000-0000-0000-0000-000000000001"
        );

        persistenceService.failScheduledFetch(
                claim,
                "PROVIDER_SERVER_ERROR",
                true,
                60,
                3600,
                2
        );

        assertEquals("FAILED", existing.getFetchStatus());
        assertEquals(1, existing.getFetchRetryCount());
        assertEquals("PROVIDER_SERVER_ERROR", existing.getLastFetchError());
        assertTrue(existing.getNextFetchAt().isAfter(java.time.LocalDateTime.now().plusSeconds(50)));

        existing.claimFetch(java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        ), java.time.LocalDateTime.now());
        ScheduledBillFetch secondClaim = new ScheduledBillFetch(
                91L,
                31L,
                4L,
                "KSEB",
                "ELECTRICITY",
                "{}",
                "00000000-0000-0000-0000-000000000002"
        );
        when(savedBillRepository.findByIdAndFetchClaimToken(
                91L,
                "00000000-0000-0000-0000-000000000002"
        )).thenReturn(Optional.of(existing));

        persistenceService.failScheduledFetch(
                secondClaim,
                "PROVIDER_SERVER_ERROR",
                false,
                60,
                3600,
                2
        );

        assertEquals(2, existing.getFetchRetryCount());
        assertNull(existing.getNextFetchAt());
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
        customer.setTenantId(4L);
        return customer;
    }

    private BillProvider provider(String code, String categoryCode) {
        return new BillProvider(code, code, new BillCategory(categoryCode, categoryCode));
    }
}
