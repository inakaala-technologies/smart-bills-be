package com.bhive.appointment.controller;

import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.config.SecurityConfig;
import com.bhive.common.config.AccessTokenService;
import com.bhive.common.config.AccessTokenService.AuthenticatedUser;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(controllers = {AppointmentController.class, AppointmentConfigurationController.class})
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AppointmentSecurityRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentRepository appointmentRepository;

    @MockBean
    private AppointmentServiceRepository appointmentServiceRepository;

    @MockBean
    private BusinessRepository businessRepository;

    @MockBean
    private BusinessUserRepository businessUserRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessCustomerRepository businessCustomerRepository;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private AccessTokenService accessTokenService;

    @Test
    void registrationPreflightAllowsActiveViteOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/register")
                .header("Origin", "http://localhost:5180")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5180"));
    }

    @Test
    void customerCanReadAppointmentsAndEnabledServicesThroughSecurityFilters() throws Exception {
        when(accessTokenService.verify("valid-test-token"))
            .thenReturn(Optional.of(new AuthenticatedUser(9L, List.of(5L))));
        Business business = new Business();
        business.setTenantId(5L);
        business.setAppointmentsEnabled(true);
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(9L);
        BusinessCustomer relationship = new BusinessCustomer();
        relationship.setCustomerProfileId(2L);
        relationship.setStatus("ACTIVE");
        AppointmentService service = new AppointmentService();
        service.setId(7L);
        service.setTenantId(5L);
        service.setBusinessId(1L);
        service.setName("Consultation");
        service.setPrice(new BigDecimal("250.00"));
        service.setDurationMinutes(30);
        service.setEnabled(true);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(customer));
        Mockito.when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 1L)).thenReturn(List.of(relationship));
        Mockito.when(appointmentServiceRepository.findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(5L, 1L))
            .thenReturn(List.of(service));
        Mockito.when(appointmentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/appointment-services")
                .param("businessId", "1")
                .header("Authorization", "Bearer valid-test-token")
                .header("X-Tenant-Id", "5")
                .header("X-User-Id", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Consultation"));

        mockMvc.perform(get("/api/v1/appointments")
                .header("Authorization", "Bearer valid-test-token")
                .header("X-Tenant-Id", "5")
                .header("X-User-Id", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsSpoofedIdentityHeadersWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/appointments")
                .header("X-Tenant-Id", "5")
                .header("X-User-Id", "9"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTenantNotBoundToBearerToken() throws Exception {
        when(accessTokenService.verify("valid-test-token"))
            .thenReturn(Optional.of(new AuthenticatedUser(9L, List.of(5L))));

        mockMvc.perform(get("/api/v1/appointments")
                .header("Authorization", "Bearer valid-test-token")
                .header("X-Tenant-Id", "6"))
            .andExpect(status().isForbidden());
    }
}