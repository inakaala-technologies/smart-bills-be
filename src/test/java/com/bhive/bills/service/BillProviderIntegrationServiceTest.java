package com.bhive.bills.service;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.ProviderInputField;
import com.bhive.bills.provider.BillProviderAdapter;
import com.bhive.bills.provider.BillProviderAdapterRegistry;
import com.bhive.bills.provider.BillProviderRequest;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import com.bhive.common.util.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillProviderIntegrationServiceTest {

    @Mock
    private BillProviderAdapterRegistry adapterRegistry;
    @Mock
    private ProviderInputFieldRepository inputFieldRepository;
    @Mock
    private BillProviderRepository providerRepository;
    @Mock
    private SavedBillService savedBillService;
    @Mock
    private CustomerBillConsentService customerBillConsentService;
    @Mock
    private ApiSetuConsentArtifactValidator consentArtifactValidator;
    @Mock
    private BillProviderAdapter adapter;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void saves_inputs_before_provider_fetch_and_updates_bill_after_success() throws Exception {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", " 123 ");
        ProviderFetchResponse response = new ProviderFetchResponse(
                "JSON",
                "application/json",
                new ObjectMapper().readTree("{\"bill\":\"ok\"}"),
                null,
                null
        );
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        when(adapterRegistry.getAdapter("KSEB")).thenReturn(adapter);
        when(customerBillConsentService.authorizeAndPrepareManualFetch(
                "application-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        )).thenReturn(51L);
        lenient().when(consentArtifactValidator.requireVerifiedArtifact(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> invocation.getArgument(2));
        when(adapter.fetchBill("KSEB", inputs, response.jsonData())).thenReturn(response);

        ProviderFetchResponse result = service.fetchBill(
                new BillProviderRequest(" kseb ", inputs, response.jsonData(), "application-consent")
        );

        assertEquals(response, result);
        InOrder order = inOrder(customerBillConsentService, adapter, savedBillService);
        order.verify(customerBillConsentService).authorizeAndPrepareManualFetch(
                "application-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        );
        order.verify(adapter).fetchBill("KSEB", inputs, response.jsonData());
        order.verify(savedBillService).saveFetchedBill("KSEB", inputs, response);
    }

    @Test
    void retains_inputs_and_records_failure_when_provider_fetch_fails() {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        when(adapterRegistry.getAdapter("KSEB")).thenReturn(adapter);
        JsonNode artifact = artifact();
        when(customerBillConsentService.authorizeAndPrepareManualFetch(
                "application-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        )).thenReturn(51L);
        when(consentArtifactValidator.requireVerifiedArtifact(70L, "KSEB", artifact))
                .thenReturn(artifact);
        when(adapter.fetchBill("KSEB", inputs, artifact)).thenThrow(new IllegalStateException("provider error"));

        assertThrows(
                IllegalStateException.class,
                () -> service.fetchBill(new BillProviderRequest(
                        "KSEB", inputs, artifact, "application-consent"
                ))
        );

        InOrder order = inOrder(customerBillConsentService, adapter, savedBillService);
        order.verify(customerBillConsentService).authorizeAndPrepareManualFetch(
                "application-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        );
        order.verify(adapter).fetchBill("KSEB", inputs, artifact);
        order.verify(savedBillService).recordFetchFailure(
                "KSEB",
                inputs,
                "PROVIDER_FETCH_ERROR"
        );
        verify(savedBillService, never()).saveFetchedBill("KSEB", inputs, null);
    }

    @Test
    void rejects_missing_required_inputs_before_provider_call() {
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.fetchBill(new BillProviderRequest("KSEB", Map.of(), null))
        );

        verify(adapter, never()).fetchBill("KSEB", Map.of(), null);
        verify(customerBillConsentService, never()).authorizeAndPrepareManualFetch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap()
        );
        verify(savedBillService, never()).saveFetchedBill("KSEB", Map.of(), null);
    }

    @Test
    void does_not_call_provider_when_a_fetch_is_already_in_progress() {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "A fetch for this saved bill is already in progress."
        )).when(customerBillConsentService).authorizeAndPrepareManualFetch(
                "application-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        );
        JsonNode artifact = artifact();
        when(consentArtifactValidator.requireVerifiedArtifact(70L, "KSEB", artifact))
                .thenReturn(artifact);

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.fetchBill(new BillProviderRequest(
                        "KSEB", inputs, artifact, "application-consent"
                ))
        );

        verify(adapter, never()).fetchBill("KSEB", inputs, artifact);
        verify(savedBillService, never()).recordFetchFailure("KSEB", inputs, "PROVIDER_FETCH_ERROR");
    }

    @Test
    void rejects_unsupported_categories_before_calling_provider() {
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "OTHER");
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.fetchBill(new BillProviderRequest(
                        "KSEB", Map.of("ConsumerNo", "123"), null
                ))
        );

        verify(customerBillConsentService, never()).authorizeAndPrepareManualFetch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap()
        );
        verify(adapter, never()).fetchBill("KSEB", Map.of("ConsumerNo", "123"), null);
    }

    @Test
    void manually_fetches_and_persists_challan_using_documented_inputs() throws Exception {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("TRANSPORT_ECHALLAN", "ECHALLAN");
        Map<String, String> inputs = Map.of(
                "REQUEST", "INPUT_REQUEST",
                "dl_rc", "INPUT_DL_RC",
                "FullName", "INPUT_FULLNAME"
        );
        ProviderFetchResponse response = new ProviderFetchResponse(
                "PDF", "application/pdf", null, null, new byte[] {0x25, 0x50}
        );
        when(providerRepository.findByCodeAndActiveTrue("TRANSPORT_ECHALLAN"))
                .thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc(
                "TRANSPORT_ECHALLAN"
        )).thenReturn(List.of(
                new ProviderInputField(provider, "REQUEST", "REQUEST", "text", true, null, null, 1),
                new ProviderInputField(provider, "dl_rc", "dl_rc", "text", true, null, null, 2),
                new ProviderInputField(provider, "FullName", "FullName", "text", true, null, null, 3)
        ));
        when(adapterRegistry.getAdapter("TRANSPORT_ECHALLAN")).thenReturn(adapter);
        JsonNode artifact = artifact();
        when(customerBillConsentService.authorizeAndPrepareManualFetch(
                "application-consent", 70L, "TRANSPORT_ECHALLAN",
                List.of("FullName", "REQUEST", "dl_rc"), inputs
        )).thenReturn(52L);
        when(consentArtifactValidator.requireVerifiedArtifact(
                70L, "TRANSPORT_ECHALLAN", artifact
        )).thenReturn(artifact);
        when(adapter.fetchBill("TRANSPORT_ECHALLAN", inputs, artifact)).thenReturn(response);

        assertEquals(response, service.fetchBill(
                new BillProviderRequest(
                        "TRANSPORT_ECHALLAN", inputs, artifact, "application-consent"
                )
        ));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                customerBillConsentService, adapter, savedBillService
        );
        order.verify(customerBillConsentService).authorizeAndPrepareManualFetch(
                "application-consent", 70L, "TRANSPORT_ECHALLAN",
                List.of("FullName", "REQUEST", "dl_rc"), inputs
        );
        order.verify(adapter).fetchBill("TRANSPORT_ECHALLAN", inputs, artifact);
        order.verify(savedBillService).saveFetchedBill("TRANSPORT_ECHALLAN", inputs, response);
    }

    @Test
    void rejects_unverified_artifact_before_saved_attempt_or_provider_adapter() {
        BillProviderIntegrationService service = new BillProviderIntegrationService(
                adapterRegistry,
                inputFieldRepository,
                providerRepository,
                savedBillService,
                customerBillConsentService,
                new UnavailableApiSetuConsentArtifactValidator()
        );
        when(savedBillService.requireAuthenticatedCustomerId()).thenReturn(70L);
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        JsonNode artifact = artifact();
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));

        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.fetchBill(new BillProviderRequest("KSEB", inputs, artifact))
        );

        assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
        verify(adapterRegistry, never()).getAdapter("KSEB");
        verify(adapter, never()).fetchBill("KSEB", inputs, artifact);
        verify(customerBillConsentService, never()).authorizeAndPrepareManualFetch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }

    private BillProviderIntegrationService service() {
        lenient().when(consentArtifactValidator.requireVerifiedArtifact(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(savedBillService.requireAuthenticatedCustomerId()).thenReturn(70L);
        return new BillProviderIntegrationService(
                adapterRegistry,
                inputFieldRepository,
                providerRepository,
                savedBillService,
                customerBillConsentService,
                consentArtifactValidator
        );
    }

    @Test
    void manual_fetch_without_matching_application_consent_never_reaches_adapter() {
        TenantContext.setUserId(7L);
        TenantContext.setTenantId(9L);
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        JsonNode artifact = artifact();
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        when(adapterRegistry.getAdapter("KSEB")).thenReturn(adapter);
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Consent record was not found."
        )).when(customerBillConsentService).authorizeAndPrepareManualFetch(
                "missing-consent", 70L, "KSEB", List.of("ConsumerNo"), inputs
        );

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.fetchBill(new BillProviderRequest(
                        "KSEB", inputs, artifact, "missing-consent"
                ))
        );

        verify(adapter, never()).fetchBill("KSEB", inputs, artifact);
        verify(savedBillService, never()).saveFetchedBill("KSEB", inputs, null);
    }

    @Test
    void scheduled_fetch_rejects_unavailable_or_invalid_artifact_before_adapter_selection() {
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        ScheduledBillFetch claim = new ScheduledBillFetch(
                51L, 70L, 8L, "KSEB", "ELECTRICITY",
                "{\"ConsumerNo\":\"123\"}", "claim-token"
        );
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        BillProviderIntegrationService unavailableService = new BillProviderIntegrationService(
                adapterRegistry,
                inputFieldRepository,
                providerRepository,
                savedBillService,
                customerBillConsentService,
                new UnavailableApiSetuConsentArtifactValidator()
        );

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> unavailableService.fetchScheduledBill(claim, "KSEB", inputs, artifact()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> unavailableService.fetchScheduledBill(claim, "KSEB", inputs, null));
        verify(adapterRegistry, never()).getAdapter("KSEB");
        verify(adapter, never()).fetchBill("KSEB", inputs, artifact());

        BillProviderIntegrationService invalidService = service();
        when(consentArtifactValidator.requireVerifiedArtifact(70L, "KSEB", artifact()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Artifact verification failed."
                ));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> invalidService.fetchScheduledBill(claim, "KSEB", inputs, artifact()));
        verify(adapterRegistry, never()).getAdapter("KSEB");
        verify(adapter, never()).fetchBill("KSEB", inputs, artifact());
    }

    @Test
    void validated_scheduled_artifact_is_passed_to_adapter_after_validation() throws Exception {
        BillProvider provider = provider("KSEB", "ELECTRICITY");
        Map<String, String> inputs = Map.of("ConsumerNo", "123");
        JsonNode supplied = artifact();
        JsonNode verified = new ObjectMapper().readTree("{\"verified\":true}");
        ScheduledBillFetch claim = new ScheduledBillFetch(
                51L, 70L, 8L, "KSEB", "ELECTRICITY",
                "{\"ConsumerNo\":\"123\"}", "claim-token"
        );
        ProviderFetchResponse response = new ProviderFetchResponse(
                "JSON", "application/json", new ObjectMapper().readTree("{\"bill\":\"ok\"}"),
                null, null
        );
        when(providerRepository.findByCodeAndActiveTrue("KSEB")).thenReturn(Optional.of(provider));
        when(inputFieldRepository.findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc("KSEB"))
                .thenReturn(List.of(new ProviderInputField(
                        provider, "ConsumerNo", "Consumer Number", "text", true, null, null, 1
                )));
        BillProviderIntegrationService integrationService = service();
        when(consentArtifactValidator.requireVerifiedArtifact(70L, "KSEB", supplied))
                .thenReturn(verified);
        when(adapterRegistry.getAdapter("KSEB")).thenReturn(adapter);
        when(adapter.fetchBill("KSEB", inputs, verified)).thenReturn(response);

        assertEquals(response, integrationService.fetchScheduledBill(claim, "KSEB", inputs, supplied));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                consentArtifactValidator, adapterRegistry, adapter
        );
        order.verify(consentArtifactValidator).requireVerifiedArtifact(70L, "KSEB", supplied);
        order.verify(adapterRegistry).getAdapter("KSEB");
        order.verify(adapter).fetchBill("KSEB", inputs, verified);
    }

    private JsonNode artifact() {
        return new ObjectMapper().createObjectNode().put("unverified", true);
    }

    private BillProvider provider(String code, String categoryCode) {
        return new BillProvider(
                code,
                code,
                new BillCategory(categoryCode, categoryCode)
        );
    }
}
