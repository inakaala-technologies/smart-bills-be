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
        when(adapter.fetchBill("KSEB", inputs, response.jsonData())).thenReturn(response);

        ProviderFetchResponse result = service.fetchBill(
                new BillProviderRequest(" kseb ", inputs, response.jsonData())
        );

        assertEquals(response, result);
        InOrder order = inOrder(savedBillService, adapter);
        order.verify(savedBillService).prepareFetchAttempt("KSEB", inputs);
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
        when(adapter.fetchBill("KSEB", inputs, null)).thenThrow(new IllegalStateException("provider error"));

        assertThrows(
                IllegalStateException.class,
                () -> service.fetchBill(new BillProviderRequest("KSEB", inputs, null))
        );

        InOrder order = inOrder(savedBillService, adapter);
        order.verify(savedBillService).prepareFetchAttempt("KSEB", inputs);
        order.verify(adapter).fetchBill("KSEB", inputs, null);
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
        verify(savedBillService, never()).prepareFetchAttempt("KSEB", Map.of());
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
        )).when(savedBillService).prepareFetchAttempt("KSEB", inputs);

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.fetchBill(new BillProviderRequest("KSEB", inputs, null))
        );

        verify(adapter, never()).fetchBill("KSEB", inputs, null);
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

        verify(savedBillService, never()).prepareFetchAttempt(
                "KSEB",
                Map.of("ConsumerNo", "123")
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
        when(adapter.fetchBill("TRANSPORT_ECHALLAN", inputs, null)).thenReturn(response);

        assertEquals(response, service.fetchBill(
                new BillProviderRequest("TRANSPORT_ECHALLAN", inputs, null)
        ));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(savedBillService, adapter);
        order.verify(savedBillService).prepareFetchAttempt("TRANSPORT_ECHALLAN", inputs);
        order.verify(adapter).fetchBill("TRANSPORT_ECHALLAN", inputs, null);
        order.verify(savedBillService).saveFetchedBill("TRANSPORT_ECHALLAN", inputs, response);
    }

    private BillProviderIntegrationService service() {
        return new BillProviderIntegrationService(
                adapterRegistry,
                inputFieldRepository,
                providerRepository,
                savedBillService
        );
    }

    private BillProvider provider(String code, String categoryCode) {
        return new BillProvider(
                code,
                code,
                new BillCategory(categoryCode, categoryCode)
        );
    }
}
