package com.bhive.bills.service;

import com.bhive.bills.config.BillFetchSchedulerProperties;
import com.bhive.bills.provider.ProviderApiException;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class AutomaticBillFetchSchedulerTest {

    @Mock
    private SavedBillPersistenceService persistenceService;
    @Mock
    private BillProviderIntegrationService integrationService;
    @Mock
    private SavedBillService savedBillService;
    @Mock
    private ScheduledBillConsentProvider consentProvider;

    @Test
    void fetches_due_bill_using_stored_inputs_and_persists_success() throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch claim = claim(51L, 70L, 8L);
        var consent = new ObjectMapper().readTree("{\"consent\":{\"id\":\"signed\"}}");
        ProviderFetchResponse response = new ProviderFetchResponse(
                "JSON",
                "application/json",
                new ObjectMapper().readTree("{\"bill\":\"fresh\"}"),
                null,
                null
        );
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(claim));
        when(consentProvider.findValidConsent(claim)).thenReturn(Optional.of(consent));
        when(integrationService.fetchScheduledBill(
                claim,
                "KSEB",
                java.util.Map.of("ConsumerNo", "K-1"),
                consent
        )).thenReturn(response);

        scheduler.fetchDueBills();

        verify(savedBillService).completeScheduledFetch(claim, response);
        verify(savedBillService, never()).failScheduledFetch(
                any(), any(), eq(false), anyLong(), anyLong(), anyInt()
        );
    }

    @Test
    void defers_missing_consent_without_consuming_retries_or_calling_the_provider() {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch claim = claim(51L, 70L, 8L);
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(claim));
        when(consentProvider.findValidConsent(claim)).thenReturn(Optional.empty());

        scheduler.fetchDueBills();

        verify(savedBillService).deferScheduledFetch(claim, 24L);
        verify(savedBillService, never()).failScheduledFetch(
                any(), any(), eq(false), anyLong(), anyLong(), anyInt()
        );
        verify(integrationService, never()).fetchScheduledBill(any(), any(), any(), any());
    }

    @Test
    void defers_when_the_default_scheduled_consent_source_is_unavailable() {
        BillFetchSchedulerProperties properties = new BillFetchSchedulerProperties();
        AutomaticBillFetchScheduler scheduler = new AutomaticBillFetchScheduler(
                persistenceService,
                integrationService,
                savedBillService,
                new UnavailableScheduledBillConsentProvider(),
                properties,
                new ObjectMapper()
        );
        ScheduledBillFetch claim = claim(51L, 70L, 8L);
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(claim));

        scheduler.fetchDueBills();

        verify(savedBillService).deferScheduledFetch(claim, 24L);
        verify(savedBillService, never()).failScheduledFetch(
                any(), any(), eq(false), anyLong(), anyLong(), anyInt()
        );
        verify(integrationService, never()).fetchScheduledBill(any(), any(), any(), any());
    }

    @Test
    void defers_challan_when_consent_does_not_explicitly_allow_recurring_access() throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch claim = new ScheduledBillFetch(
                53L, 70L, 8L, "TRANSPORT_ECHALLAN", "ECHALLAN",
                "{\"REQUEST\":\"INPUT_REQUEST\",\"dl_rc\":\"INPUT_DL_RC\",\"FullName\":\"INPUT_FULLNAME\"}",
                "token-53"
        );
        JsonNode consent = mock(JsonNode.class);
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(claim));
        when(consentProvider.findValidConsent(claim)).thenReturn(Optional.of(consent));
        when(consentProvider.allowsRecurringAccess(claim, consent)).thenReturn(false);

        scheduler.fetchDueBills();

        verify(savedBillService).deferScheduledFetch(claim, 24L);
        verify(savedBillService, never()).failScheduledFetch(
                any(), any(), eq(false), anyLong(), anyLong(), anyInt()
        );
        verify(integrationService, never()).fetchScheduledBill(any(), any(), any(), any());
    }

    @Test
    void calls_challan_provider_only_when_trusted_consent_source_confirms_recurring_access()
            throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch claim = new ScheduledBillFetch(
                54L, 70L, 8L, "TRANSPORT_ECHALLAN", "ECHALLAN",
                "{\"REQUEST\":\"INPUT_REQUEST\",\"dl_rc\":\"INPUT_DL_RC\",\"FullName\":\"INPUT_FULLNAME\"}",
                "token-54"
        );
        JsonNode consent = mock(JsonNode.class);
        ProviderFetchResponse response = new ProviderFetchResponse(
                "PDF", "application/pdf", null, null, new byte[] {0x25, 0x50}
        );
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(claim));
        when(consentProvider.findValidConsent(claim)).thenReturn(Optional.of(consent));
        when(consentProvider.allowsRecurringAccess(claim, consent)).thenReturn(true);
        when(integrationService.fetchScheduledBill(
                claim,
                "TRANSPORT_ECHALLAN",
                java.util.Map.of(
                        "REQUEST", "INPUT_REQUEST",
                        "dl_rc", "INPUT_DL_RC",
                        "FullName", "INPUT_FULLNAME"
                ),
                consent
        )).thenReturn(response);

        scheduler.fetchDueBills();

        verify(savedBillService).completeScheduledFetch(claim, response);
        verify(savedBillService, never()).deferScheduledFetch(claim, 24L);
    }

    @Test
    void temporary_and_permanent_provider_errors_are_classified_differently() throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch temporaryClaim = claim(51L, 70L, 8L);
        ScheduledBillFetch permanentClaim = new ScheduledBillFetch(
                52L, 71L, 9L, "KSEB", "ELECTRICITY",
                "{\"ConsumerNo\":\"K-2\"}", "token-52"
        );
        var consent = new ObjectMapper().readTree("{\"consent\":{\"id\":\"signed\"}}");
        when(consentProvider.findValidConsent(temporaryClaim)).thenReturn(Optional.of(consent));
        when(consentProvider.findValidConsent(permanentClaim)).thenReturn(Optional.of(consent));
        when(integrationService.fetchScheduledBill(
                eq(temporaryClaim), eq("KSEB"),
                eq(java.util.Map.of("ConsumerNo", "K-1")), eq(consent)
        )).thenThrow(new ResourceAccessException("timeout"));
        when(integrationService.fetchScheduledBill(
                eq(permanentClaim), eq("KSEB"),
                eq(java.util.Map.of("ConsumerNo", "K-2")), eq(consent)
        )).thenThrow(new ProviderApiException(HttpStatus.BAD_REQUEST, "KSEB"));
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(temporaryClaim, permanentClaim));

        scheduler.fetchDueBills();

        verify(savedBillService).failScheduledFetch(
                temporaryClaim,
                "PROVIDER_CONNECTION_ERROR",
                true,
                300L,
                21_600L,
                5
        );
        verify(savedBillService).failScheduledFetch(
                permanentClaim,
                "PROVIDER_HTTP_ERROR",
                false,
                300L,
                21_600L,
                5
        );
    }

    @Test
    void continues_processing_after_one_record_fails() throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch failedClaim = claim(51L, 70L, 8L);
        ScheduledBillFetch successfulClaim = new ScheduledBillFetch(
                52L, 71L, 9L, "KSEB", "ELECTRICITY",
                "{\"ConsumerNo\":\"K-2\"}", "token-52"
        );
        var consent = new ObjectMapper().readTree("{\"consent\":{\"id\":\"signed\"}}");
        ProviderFetchResponse response = new ProviderFetchResponse(
                "JSON",
                "application/json",
                new ObjectMapper().readTree("{\"bill\":\"fresh\"}"),
                null,
                null
        );
        when(persistenceService.claimEligibleFetches(
                any(), eq(10), eq(15L)
        )).thenReturn(List.of(failedClaim, successfulClaim));
        when(consentProvider.findValidConsent(failedClaim)).thenReturn(Optional.of(consent));
        when(consentProvider.findValidConsent(successfulClaim)).thenReturn(Optional.of(consent));
        when(integrationService.fetchScheduledBill(
                eq(failedClaim), eq("KSEB"),
                eq(java.util.Map.of("ConsumerNo", "K-1")), eq(consent)
        )).thenThrow(new ResourceAccessException("timeout"));
        when(integrationService.fetchScheduledBill(
                eq(successfulClaim), eq("KSEB"),
                eq(java.util.Map.of("ConsumerNo", "K-2")), eq(consent)
        )).thenReturn(response);

        scheduler.fetchDueBills();

        verify(savedBillService).failScheduledFetch(
                failedClaim,
                "PROVIDER_CONNECTION_ERROR",
                true,
                300L,
                21_600L,
                5
        );
        verify(savedBillService).completeScheduledFetch(successfulClaim, response);
    }

    @Test
    void defers_unavailable_artifact_validation_without_consuming_retries() throws Exception {
        AutomaticBillFetchScheduler scheduler = scheduler();
        ScheduledBillFetch claim = claim(51L, 70L, 8L);
        JsonNode consent = new ObjectMapper().readTree("{\"consent\":{\"id\":\"provided\"}}");
        when(persistenceService.claimEligibleFetches(any(), eq(10), eq(15L)))
                .thenReturn(List.of(claim));
        when(consentProvider.findValidConsent(claim)).thenReturn(Optional.of(consent));
        when(integrationService.fetchScheduledBill(
                claim,
                "KSEB",
                java.util.Map.of("ConsumerNo", "K-1"),
                consent
        )).thenThrow(new org.springframework.web.server.ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "A verified API Setu consent artifact is not available."
        ));

        scheduler.fetchDueBills();

        verify(savedBillService).deferScheduledFetch(claim, 24L);
        verify(savedBillService, never()).failScheduledFetch(
                any(), any(), eq(false),
                anyLong(), anyLong(), anyInt()
        );
    }

    private AutomaticBillFetchScheduler scheduler() {
        BillFetchSchedulerProperties properties = new BillFetchSchedulerProperties();
        return new AutomaticBillFetchScheduler(
                persistenceService,
                integrationService,
                savedBillService,
                consentProvider,
                properties,
                new ObjectMapper()
        );
    }

    private ScheduledBillFetch claim(Long id, Long customerId, Long tenantId) {
        return new ScheduledBillFetch(
                id,
                customerId,
                tenantId,
                "KSEB",
                "ELECTRICITY",
                "{\"ConsumerNo\":\"K-1\"}",
                "token-" + id
        );
    }
}
