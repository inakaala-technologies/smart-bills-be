package com.bhive.membership.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhive.business.entity.Business;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipNotification;
import com.bhive.membership.entity.MembershipPlan;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipNotificationRepository;
import com.bhive.membership.repository.MembershipPlanRepository;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MembershipManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class MembershipManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MembershipPlanRepository planRepository;

    @MockBean
    private MembershipRepository membershipRepository;

    @MockBean
    private MembershipNotificationRepository notificationRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessCustomerRepository businessCustomerRepository;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private BusinessRepository businessRepository;

    @MockBean
    private NotificationService notificationService;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(8L);
        Business business = new Business();
        business.setId(10L);
        business.setTenantId(5L);
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(true);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsPlanUnderAuthorizedBusiness() throws Exception {
        when(planRepository.save(any(MembershipPlan.class))).thenAnswer(invocation -> {
            MembershipPlan plan = invocation.getArgument(0);
            plan.setId(21L);
            return plan;
        });

        mockMvc.perform(post("/api/v1/businesses/10/membership-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Monthly\",\"price\":1500,\"billingFrequency\":\"MONTHLY\",\"durationMonths\":1,\"active\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(21))
            .andExpect(jsonPath("$.businessId").value(10))
            .andExpect(jsonPath("$.price").value(1500));
    }

            @Test
            void rejectsPlanCreationForUserWithoutBusinessAccess() throws Exception {
            when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(false);

            mockMvc.perform(post("/api/v1/businesses/10/membership-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Monthly\",\"price\":1500,\"billingFrequency\":\"MONTHLY\",\"durationMonths\":1,\"active\":true}"))
                .andExpect(status().isForbidden());
            }

    @Test
    void createsLocalMemberAndSubscriptionFromPlanSnapshot() throws Exception {
        MembershipPlan plan = new MembershipPlan();
        plan.setId(21L);
        plan.setBusinessId(10L);
        plan.setTenantId(5L);
        plan.setName("Monthly");
        plan.setPrice(new BigDecimal("1500.00"));
        plan.setDurationMonths(1);
        plan.setActive(true);
        when(planRepository.findByIdAndBusinessIdAndTenantId(21L, 10L, 5L)).thenReturn(Optional.of(plan));
        when(customerProfileRepository.findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNull("new@example.com", 5L)).thenReturn(Optional.empty());
        when(customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNull("9876543210", 5L)).thenReturn(Optional.empty());
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> {
            CustomerProfile profile = invocation.getArgument(0);
            profile.setId(31L);
            return profile;
        });
        when(businessCustomerRepository.findByBusinessIdAndCustomerProfileId(10L, 31L)).thenReturn(Optional.empty());
        when(businessCustomerRepository.save(any(BusinessCustomer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> {
            Membership membership = invocation.getArgument(0);
            membership.setId(41L);
            return membership;
        });

        mockMvc.perform(post("/api/v1/businesses/10/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":21,\"memberName\":\"Ravi Kumar\",\"email\":\"new@example.com\",\"phone\":\"9876543210\",\"startDate\":\"2026-10-01\",\"paymentStatus\":\"PAID\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").value(31))
            .andExpect(jsonPath("$.planName").value("Monthly"))
            .andExpect(jsonPath("$.price").value(1500.00))
            .andExpect(jsonPath("$.endDate").value("2026-11-01"))
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void notifiesRegisteredCustomerWhenSubscriptionIsCreated() throws Exception {
        MembershipPlan plan = new MembershipPlan();
        plan.setId(21L);
        plan.setBusinessId(10L);
        plan.setTenantId(5L);
        plan.setName("Monthly");
        plan.setPrice(new BigDecimal("1500.00"));
        plan.setDurationMonths(1);
        plan.setActive(true);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(31L);
        profile.setTenantId(5L);
        profile.setUserId(18L);
        profile.setProfileId("CUS12345");
        profile.setName("Ravi Kumar");
        when(planRepository.findByIdAndBusinessIdAndTenantId(21L, 10L, 5L)).thenReturn(Optional.of(plan));
        when(customerProfileRepository.findAll()).thenReturn(List.of(profile));
        when(businessCustomerRepository.findByBusinessIdAndCustomerProfileId(10L, 31L)).thenReturn(Optional.of(new BusinessCustomer()));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> {
            Membership membership = invocation.getArgument(0);
            membership.setId(41L);
            return membership;
        });

        mockMvc.perform(post("/api/v1/businesses/10/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":21,\"profileId\":\"CUS12345\",\"startDate\":\"2026-10-01\",\"paymentStatus\":\"PAID\"}"))
            .andExpect(status().isCreated());

        Mockito.verify(notificationService).create(
            5L, 18L, "MEMBERSHIP_CREATED", "Membership created",
            "Your Monthly membership is active.", "/dashboard"
        );
    }

    @Test
    void updatesMembershipWithoutChangingCustomerOwnership() throws Exception {
        Membership membership = new Membership();
        membership.setId(41L);
        membership.setTenantId(5L);
        membership.setBusinessId(10L);
        membership.setCustomerId(31L);
        membership.setPlanId(21L);
        membership.setPlanName("Monthly");
        MembershipPlan plan = new MembershipPlan();
        plan.setId(22L);
        plan.setBusinessId(10L);
        plan.setTenantId(5L);
        plan.setName("Annual");
        plan.setPrice(new BigDecimal("12000.00"));
        plan.setDurationMonths(12);
        plan.setActive(true);
        when(membershipRepository.findByIdAndBusinessIdAndTenantId(41L, 10L, 5L)).thenReturn(Optional.of(membership));
        when(planRepository.findByIdAndBusinessIdAndTenantId(22L, 10L, 5L)).thenReturn(Optional.of(plan));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/businesses/10/memberships/41")
                .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planId\":22,\"startDate\":\"2026-10-05\",\"endDate\":\"2027-01-05\",\"paymentStatus\":\"PAID\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value(31))
            .andExpect(jsonPath("$.planName").value("Annual"))
            .andExpect(jsonPath("$.price").value(12000.00))
            .andExpect(jsonPath("$.startDate").value("2026-10-05"))
            .andExpect(jsonPath("$.endDate").value("2027-01-05"))
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

            @Test
            void rejectsMembershipUpdatesForUsersWithoutBusinessAccess() throws Exception {
            when(businessMembershipService.userHasAccessToBusiness(8L, 10L, 5L)).thenReturn(false);

            mockMvc.perform(put("/api/v1/businesses/10/memberships/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":22,\"startDate\":\"2026-10-05\",\"paymentStatus\":\"PAID\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
            }

    @Test
    void preventsDuplicateRenewalReminderWithinTwentyFourHours() throws Exception {
        Membership membership = new Membership();
        membership.setId(41L);
        membership.setTenantId(5L);
        membership.setBusinessId(10L);
        membership.setCustomerId(31L);
        membership.setPlanName("Monthly");
        membership.setEndDate(LocalDate.now().plusDays(3));
        MembershipNotification previous = new MembershipNotification();
        previous.setSentAt(LocalDateTime.now().minusHours(2));
        when(membershipRepository.findById(41L)).thenReturn(Optional.of(membership));
        when(notificationRepository.findByBusinessIdAndMembershipIdOrderBySentAtDesc(10L, 41L)).thenReturn(List.of(previous));

        mockMvc.perform(post("/api/v1/memberships/41/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict());

        Mockito.verify(notificationRepository, Mockito.never()).save(any(MembershipNotification.class));
    }

    @Test
    void sendsRenewalReminderToRegisteredCustomerInbox() throws Exception {
        Membership membership = new Membership();
        membership.setId(42L);
        membership.setTenantId(5L);
        membership.setBusinessId(10L);
        membership.setCustomerId(31L);
        membership.setPlanName("Monthly");
        membership.setEndDate(LocalDate.now().plusDays(3));
        CustomerProfile profile = new CustomerProfile();
        profile.setId(31L);
        profile.setTenantId(5L);
        profile.setUserId(18L);
        when(membershipRepository.findById(42L)).thenReturn(Optional.of(membership));
        when(notificationRepository.findByBusinessIdAndMembershipIdOrderBySentAtDesc(10L, 42L)).thenReturn(List.of());
        when(customerProfileRepository.findById(31L)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/api/v1/memberships/42/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated());

        Mockito.verify(notificationService).create(
            5L,
            18L,
            "MEMBERSHIP_REMINDER",
            "Membership reminder",
            "Your Monthly membership is due for renewal on " + membership.getEndDate() + ". Contact the business to renew.",
            "/dashboard"
        );
    }
}