package com.bhive.membership.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
class MembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MembershipRepository membershipRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllMembershipsShouldReturnList() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(8L);

        Membership membership = new Membership();
        membership.setId(1L);
        membership.setTenantId(5L);
        membership.setBusinessId(10L);
        membership.setCustomerId(11L);
        membership.setPlanName("Gold");
        membership.setPrice(new BigDecimal("4999.00"));
        membership.setStartDate(LocalDate.now());
        membership.setEndDate(LocalDate.now().plusMonths(1));
        membership.setStatus(MembershipStatus.ACTIVE);

        when(membershipRepository.findAll()).thenReturn(List.of(membership));
        when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/memberships")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].planName").value("Gold"));
    }

    @Test
    void createMembershipShouldRejectPastEndDate() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(8L);
        when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":5,\"businessId\":10,\"customerId\":11,\"planName\":\"Gold\",\"price\":4999.00,\"startDate\":\"2026-09-30\",\"endDate\":\"2026-09-15\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createMembershipNotifiesRegisteredCustomer() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(8L);
        when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(true);
        Membership membership = new Membership();
        membership.setTenantId(5L);
        membership.setBusinessId(10L);
        membership.setCustomerId(11L);
        membership.setPlanName("Gold");
        when(membershipRepository.save(org.mockito.ArgumentMatchers.any(Membership.class))).thenReturn(membership);
        CustomerProfile customer = new CustomerProfile();
        customer.setId(11L);
        customer.setTenantId(5L);
        customer.setUserId(18L);
        when(customerProfileRepository.findById(11L)).thenReturn(Optional.of(customer));

        mockMvc.perform(post("/api/v1/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":5,\"businessId\":10,\"customerId\":11,\"planName\":\"Gold\",\"price\":4999.00,\"startDate\":\"2026-10-01\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isCreated());

        verify(notificationService).create(
            5L, 18L, "MEMBERSHIP_CREATED", "Membership created", "Your Gold membership was created.", "/dashboard"
        );
    }
}
