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
    void saves_bill_only_after_provider_fetch_succeeds() throws Exception {
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
        when(savedBillService.requireAuthenticatedCustomerId()).thenReturn(30L);

        ProviderFetchResponse result = service.fetchBill(
                new BillProviderRequest(" kseb ", inputs, response.jsonData())
        );

        assertEquals(response, result);
        InOrder order = inOrder(adapter, savedBillService);
        order.verify(adapter).fetchBill("KSEB", inputs, response.jsonData());
        order.verify(savedBillService).saveFetchedBill("KSEB", inputs, response);
    }

    @Test
    void does_not_save_when_provider_fetch_fails() {
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
        when(savedBillService.requireAuthenticatedCustomerId()).thenReturn(30L);
        when(adapter.fetchBill("KSEB", inputs, null)).thenThrow(new IllegalStateException("provider error"));

        assertThrows(
                IllegalStateException.class,
                () -> service.fetchBill(new BillProviderRequest("KSEB", inputs, null))
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
        verify(savedBillService, never()).saveFetchedBill("KSEB", Map.of(), null);
    }

    @Test
    void rejects_non_bill_categories_before_calling_provider() {
        BillProviderIntegrationService service = service();
        BillProvider provider = provider("KSEB", "INSURANCE");
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

        verify(savedBillService, never()).requireAuthenticatedCustomerId();
        verify(adapter, never()).fetchBill("KSEB", Map.of("ConsumerNo", "123"), null);
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
