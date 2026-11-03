package com.bhive.bills.service;

import com.bhive.bills.dto.SavedBillResponse;
import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedBillServiceTest {

    @Mock
    private SavedBillRepository savedBillRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private SavedBillPersistenceService persistenceService;
    @Mock
    private ConsumerIdentityResolver identityResolver;
    private SavedBillService savedBillService;

    @BeforeEach
    void setUp() {
        savedBillService = new SavedBillService(
                savedBillRepository,
                customerProfileRepository,
                persistenceService,
                identityResolver,
                new ObjectMapper()
        );
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void stores_only_valid_successful_provider_responses() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        CustomerProfile customer = customer(30L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer));
        when(identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")))
                .thenReturn(new ConsumerIdentity("K-1", "hash", List.of("ConsumerNo")));
        ObjectNode json = new ObjectMapper().createObjectNode().put("billNo", "B-1");

        savedBillService.saveFetchedBill(
                "KSEB",
                Map.of("ConsumerNo", "K-1"),
                new ProviderFetchResponse("JSON", "application/json", json, null, null)
        );

        verify(persistenceService).completeManualFetch(
                30L, "KSEB", "hash",
                "{\"ConsumerNo\":\"K-1\"}", "JSON", "application/json",
                "{\"billNo\":\"B-1\"}", null, null
        );
    }

    @Test
    void persists_xml_and_pdf_payloads_without_reencoding() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        when(identityResolver.resolve("KIADB", Map.of("RegNum", "R-1")))
                .thenReturn(new ConsumerIdentity("R-1", "hash", List.of("RegNum")));
        byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, (byte) 0xff};

        savedBillService.saveFetchedBill(
                "KIADB",
                Map.of("RegNum", "R-1"),
                new ProviderFetchResponse("PDF", "application/pdf", null, null, pdf)
        );

        verify(persistenceService).completeManualFetch(
                30L, "KIADB", "hash",
                "{\"RegNum\":\"R-1\"}", "PDF", "application/pdf", null, null, pdf
        );
    }

    @Test
    void completes_only_the_previously_prepared_manual_fetch() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        when(identityResolver.resolve("KSEB", Map.of("ConsumerNo", "K-1")))
                .thenReturn(new ConsumerIdentity("K-1", "hash", List.of("ConsumerNo")));
        savedBillService.saveFetchedBill(
                "KSEB",
                Map.of("ConsumerNo", "K-1"),
                new ProviderFetchResponse(
                        "JSON",
                        "application/json",
                        new ObjectMapper().createObjectNode().put("billNo", "B-1"),
                        null,
                        null
                )
        );

        verify(persistenceService).completeManualFetch(
                30L,
                "KSEB",
                "hash",
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"billNo\":\"B-1\"}",
                null,
                null
        );
    }

    @Test
    void rejects_missing_authentication_or_invalid_provider_response_without_persisting() {
        ResponseStatusException unauthenticated = assertThrows(
                ResponseStatusException.class,
                () -> savedBillService.saveFetchedBill(
                        "KSEB",
                        Map.of("ConsumerNo", "K-1"),
                        new ProviderFetchResponse("JSON", "application/json", null, null, null)
                )
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());

        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        assertThrows(
                ResponseStatusException.class,
                () -> savedBillService.saveFetchedBill(
                        "KSEB",
                        Map.of("ConsumerNo", "K-1"),
                        new ProviderFetchResponse("JSON", "application/json", null, null, null)
                )
        );
        verify(persistenceService, never()).completeManualFetch(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void scopes_saved_bill_lookup_to_authenticated_customer_profile() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        when(savedBillRepository.findByIdAndCustomer_Id(500L, 30L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> savedBillService.getSavedBill(500L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(savedBillRepository).findByIdAndCustomer_Id(500L, 30L);
    }

    @Test
    void retrieves_customer_bills_filtered_by_category() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        Page<SavedBill> page = new PageImpl<>(List.of());
        when(savedBillRepository.findAllByCustomer_IdAndCategory_Code(
                30L, "WATER", PageRequest.of(0, 10)
        )).thenReturn(page);

        Page<SavedBillResponse> result = savedBillService.getSavedBills(
                "water",
                PageRequest.of(0, 10)
        );

        assertEquals(0, result.getTotalElements());
        verify(savedBillRepository).findAllByCustomer_IdAndCategory_Code(
                30L, "WATER", PageRequest.of(0, 10)
        );
    }

    @Test
    void retrieves_all_saved_bills_for_authenticated_customer() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
        PageRequest pageable = PageRequest.of(0, 10);
        when(savedBillRepository.findAllByCustomer_Id(30L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<SavedBillResponse> result = savedBillService.getSavedBills(null, pageable);

        assertEquals(0, result.getTotalElements());
        verify(savedBillRepository).findAllByCustomer_Id(30L, pageable);
    }

    @Test
    void normalizes_empty_swagger_sort_to_default_saved_bill_sort() {
        authenticateCustomer();
        PageRequest expectedPageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("fetchedAt"))
        );
        when(savedBillRepository.findAllByCustomer_Id(30L, expectedPageable))
                .thenReturn(new PageImpl<>(List.of()));

        savedBillService.getSavedBills(
                null,
                PageRequest.of(0, 10, Sort.by("[]"))
        );

        verify(savedBillRepository).findAllByCustomer_Id(30L, expectedPageable);
    }

    @Test
    void preserves_pagination_without_a_sort_parameter() {
        authenticateCustomer();
        PageRequest pageable = PageRequest.of(0, 10);
        when(savedBillRepository.findAllByCustomer_Id(30L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        savedBillService.getSavedBills(null, pageable);

        verify(savedBillRepository).findAllByCustomer_Id(30L, pageable);
    }

    @Test
    void preserves_valid_saved_bill_sort_properties() {
        authenticateCustomer();
        PageRequest pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.asc("consumerNumber"))
        );
        when(savedBillRepository.findAllByCustomer_Id(30L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        savedBillService.getSavedBills(null, pageable);

        verify(savedBillRepository).findAllByCustomer_Id(30L, pageable);
    }

    @Test
    void rejects_invalid_sort_properties_as_bad_request_without_querying_repository() {
        authenticateCustomer();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> savedBillService.getSavedBills(
                        null,
                        PageRequest.of(0, 10, Sort.by("notASavedBillProperty"))
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(savedBillRepository, never()).findAllByCustomer_Id(
                org.mockito.ArgumentMatchers.eq(30L),
                any()
        );
    }

    private void authenticateCustomer() {
        TenantContext.setUserId(10L);
        TenantContext.setTenantId(20L);
        when(customerProfileRepository.findByUserIdAndTenantId(10L, 20L))
                .thenReturn(Optional.of(customer(30L)));
    }

    private CustomerProfile customer(Long id) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(id);
        return customer;
    }
}
