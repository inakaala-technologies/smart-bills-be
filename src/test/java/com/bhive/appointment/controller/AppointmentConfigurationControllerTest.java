package com.bhive.appointment.controller;

import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.math.BigDecimal;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentConfigurationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentServiceRepository appointmentServiceRepository;

    @MockBean
    private BusinessRepository businessRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessCustomerRepository businessCustomerRepository;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(9L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void businessCanEnableAppointmentsBeforeAddingServices() throws Exception {
        Business business = new Business();
        business.setTenantId(5L);
        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(businessRepository.save(Mockito.any(Business.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

            Mockito.when(appointmentServiceRepository.findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(5L, 1L))
                .thenReturn(List.of());

            mockMvc.perform(put("/api/v1/businesses/1/appointments-enabled").param("enabled", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add and save at least one active service before enabling appointments."));

            Mockito.verify(businessRepository, Mockito.never()).save(Mockito.any(Business.class));
    }

    @Test
    void businessCanEnableAppointmentsAfterAddingAnEnabledService() throws Exception {
        Business business = new Business();
        business.setTenantId(5L);
        AppointmentService service = new AppointmentService();
        service.setEnabled(true);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(appointmentServiceRepository.findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(5L, 1L))
            .thenReturn(List.of(service));
        Mockito.when(businessRepository.save(Mockito.any(Business.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/businesses/1/appointments-enabled").param("enabled", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appointmentsEnabled").value(true));
    }

    @Test
    void customerCanSeeEnabledServicesForLinkedBusiness() throws Exception {
        Business business = new Business();
        business.setTenantId(5L);
        business.setAppointmentsEnabled(true);
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(9L);
        BusinessCustomer link = new BusinessCustomer();
        link.setCustomerProfileId(2L);
        link.setStatus("ACTIVE");
        AppointmentService service = new AppointmentService();
        service.setId(44L);
        service.setBusinessId(1L);
        service.setName("Consultation");
        service.setDescription("Initial consultation");
        service.setPrice(new BigDecimal("500.00"));
        service.setDurationMinutes(45);
        service.setEnabled(true);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(customer));
        Mockito.when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 1L)).thenReturn(List.of(link));
        Mockito.when(appointmentServiceRepository.findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(5L, 1L))
            .thenReturn(List.of(service));

        mockMvc.perform(get("/api/v1/appointment-services").param("businessId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Consultation"))
            .andExpect(jsonPath("$[0].price").value(500.0));
    }

    @Test
    void businessCanSaveServiceDetails() throws Exception {
        Business business = new Business();
        business.setTenantId(5L);
        AppointmentService savedService = new AppointmentService();
        savedService.setId(44L);
        savedService.setTenantId(5L);
        savedService.setBusinessId(1L);
        savedService.setName("Consultation");
        savedService.setPrice(new BigDecimal("500.00"));
        savedService.setDurationMinutes(45);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(appointmentServiceRepository.findByTenantIdAndBusinessIdOrderByNameAsc(5L, 1L))
            .thenReturn(List.of(), List.of(savedService));
        Mockito.when(appointmentServiceRepository.save(Mockito.any(AppointmentService.class)))
            .thenAnswer(invocation -> {
                AppointmentService service = invocation.getArgument(0);
                service.setId(44L);
                return service;
            });

        mockMvc.perform(put("/api/v1/businesses/1/appointment-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"name\":\"Consultation\",\"description\":\"First visit\",\"price\":500,\"durationMinutes\":45,\"enabled\":true}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(44))
            .andExpect(jsonPath("$[0].price").value(500.0))
            .andExpect(jsonPath("$[0].durationMinutes").value(45));
    }
}