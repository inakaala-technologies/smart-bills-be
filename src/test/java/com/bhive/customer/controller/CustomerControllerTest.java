package com.bhive.customer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.dto.CustomerCreateRequest;
import com.bhive.customer.dto.CustomerProfileResponse;
import com.bhive.customer.dto.LocationUpdateRequest;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private BusinessCustomerRepository businessCustomerRepository;

    @InjectMocks
    private CustomerController customerController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldFilterCustomersByNameEmailOrPhoneForSearchQuery() {
        TenantContext.setTenantId(7L);

        CustomerProfile customerA = new CustomerProfile();
        customerA.setId(1L);
        customerA.setTenantId(7L);
        customerA.setName("Aarav Sharma");
        customerA.setEmail("aarav@example.com");
        customerA.setPhone("9999999999");

        CustomerProfile customerB = new CustomerProfile();
        customerB.setId(2L);
        customerB.setTenantId(7L);
        customerB.setName("Neha Rao");
        customerB.setEmail("neha@example.com");
        customerB.setPhone("8888888888");

        when(customerProfileRepository.searchByTenantId(eq(7L), eq("999"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(customerA)));

        List<CustomerProfileResponse> result = customerController.searchCustomers("999", 0, 20).content();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
    }

    @Test
    void shouldNotReturnOtherTenantsWhenTenantScopedSearchHasNoMatches() {
        TenantContext.setTenantId(99L);

        CustomerProfile customer = new CustomerProfile();
        customer.setId(12L);
        customer.setTenantId(1L);
        customer.setName("customer2");
        customer.setEmail("customer2@gmail.com");
        customer.setPhone("9876543210");

        when(customerProfileRepository.searchByTenantId(eq(99L), eq("customer2@gmail.com"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        List<CustomerProfileResponse> result = customerController.searchCustomers("customer2@gmail.com", 0, 20).content();

        assertEquals(0, result.size());
    }

    @Test
    void shouldUpdateOnlyTheCurrentCustomerLocation() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(21L);
        profile.setTenantId(7L);
        profile.setUserId(15L);
        when(customerProfileRepository.findByUserIdAndTenantId(15L, 7L)).thenReturn(Optional.of(profile));
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = customerController.updateMyLocation(new LocationUpdateRequest("United States", "California", "San Francisco", "Ignored free text", 37.7749, -122.4194));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("San Francisco, California, United States", response.getBody().locationAddress());
        assertEquals(37.7749, response.getBody().latitude());
        verify(customerProfileRepository).save(profile);
    }

    @Test
    void shouldCreateUnlinkedCustomerInsideCurrentTenant() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);
        when(customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNull("9999999999", 7L)).thenReturn(Optional.empty());
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> {
            CustomerProfile profile = invocation.getArgument(0);
            profile.setId(88L);
            return profile;
        });

        var response = customerController.createCustomer(new CustomerCreateRequest("Aarav Sharma", "aarav@example.com", "9999999999"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(88L, response.getBody().id());
        org.mockito.ArgumentCaptor<CustomerProfile> profileCaptor = org.mockito.ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertEquals(7L, profileCaptor.getValue().getTenantId());
        assertEquals(null, profileCaptor.getValue().getUserId());
    }
}
