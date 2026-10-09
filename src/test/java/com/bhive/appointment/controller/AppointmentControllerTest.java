package com.bhive.appointment.controller;

import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentRepository appointmentRepository;

    @MockBean
    private AppointmentServiceRepository appointmentServiceRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessRepository businessRepository;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private BusinessCustomerRepository businessCustomerRepository;

    @MockBean
    private BusinessUserRepository businessUserRepository;

    @MockBean
    private NotificationService notificationService;

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
    void businessUserShouldSeeAppointmentsForTheirBusiness() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setBusinessId(1L);
        appointment.setCustomerId(2L);
        appointment.setServiceName("Hair Cut");
        appointment.setScheduledAt(LocalDateTime.now().plusDays(1));
        appointment.setDurationMinutes(45);
        appointment.setStatus(AppointmentStatus.BOOKED);

        Business business = new Business();
        business.setId(1L);
        business.setTenantId(5L);
        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.empty());
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        mockMvc.perform(get("/api/v1/appointments")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].serviceName").value("Hair Cut"));
    }

    @Test
    void customerBookingUsesSignedInProfileAndStartsBooked() throws Exception {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(9L);
        customer.setName("Customer");

        Business business = new Business();
        business.setId(1L);
        business.setTenantId(5L);
        business.setAppointmentsEnabled(true);
        business.setName("BHive Demo");
        BusinessCustomer businessCustomer = new BusinessCustomer();
        businessCustomer.setCustomerProfileId(2L);
        businessCustomer.setStatus("ACTIVE");
        AppointmentService appointmentService = new AppointmentService();
        appointmentService.setId(77L);
        appointmentService.setTenantId(5L);
        appointmentService.setBusinessId(1L);
        appointmentService.setName("Hair consultation");
        appointmentService.setPrice(new java.math.BigDecimal("450.00"));
        appointmentService.setDurationMinutes(45);
        appointmentService.setEnabled(true);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(appointmentServiceRepository.findById(77L)).thenReturn(Optional.of(appointmentService));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(customer));
        Mockito.when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 1L)).thenReturn(List.of(businessCustomer));
        BusinessUser businessUser = new BusinessUser();
        businessUser.setTenantId(5L);
        businessUser.setBusinessId(1L);
        businessUser.setUserId(44L);
        businessUser.setStatus("ACTIVE");
        Mockito.when(businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(5L, 1L, "ACTIVE"))
            .thenReturn(List.of(businessUser));
        Mockito.when(appointmentRepository.save(Mockito.any(Appointment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        String requestBody = """
            {"businessId":1,"appointmentServiceId":77,"customerId":999,"serviceName":"Forged name",
             "scheduledAt":"2030-06-15T10:00:00","durationMinutes":5,
             "status":"COMPLETED","notes":"First visit"}
            """;

        mockMvc.perform(post("/api/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").value(2))
            .andExpect(jsonPath("$.status").value("BOOKED"))
            .andExpect(jsonPath("$.serviceName").value("Hair consultation"))
            .andExpect(jsonPath("$.servicePrice").value(450.0))
            .andExpect(jsonPath("$.durationMinutes").value(45))
            .andExpect(jsonPath("$.staffUserId").value(44));

        Mockito.verify(notificationService).create(
            5L, 44L, "APPOINTMENT_BOOKED", "New appointment",
            "An appointment for Hair consultation was booked with BHive Demo.", "/dashboard"
        );
    }

    @Test
    void availabilityOnlyBlocksTheStaffMemberWithAnOverlappingAppointment() throws Exception {
        LocalDate date = LocalDate.of(2030, 6, 15);
        Business business = new Business();
        business.setTenantId(5L);
        business.setAppointmentsEnabled(true);
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        BusinessCustomer businessCustomer = new BusinessCustomer();
        businessCustomer.setCustomerProfileId(2L);
        businessCustomer.setStatus("ACTIVE");
        AppointmentService appointmentService = new AppointmentService();
        appointmentService.setId(77L);
        appointmentService.setTenantId(5L);
        appointmentService.setBusinessId(1L);
        appointmentService.setDurationMinutes(30);
        appointmentService.setEnabled(true);
        BusinessUser busyStaff = new BusinessUser();
        busyStaff.setUserId(44L);
        BusinessUser availableStaff = new BusinessUser();
        availableStaff.setUserId(45L);
        Appointment bookedAppointment = new Appointment();
        bookedAppointment.setScheduledAt(date.atTime(10, 0));
        bookedAppointment.setDurationMinutes(30);
        bookedAppointment.setStatus(AppointmentStatus.BOOKED);
        bookedAppointment.setStaffUserId(44L);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(appointmentServiceRepository.findById(77L)).thenReturn(Optional.of(appointmentService));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(customer));
        Mockito.when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 1L)).thenReturn(List.of(businessCustomer));
        Mockito.when(businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(5L, 1L, "ACTIVE"))
            .thenReturn(List.of(busyStaff, availableStaff));
        Mockito.when(appointmentRepository.findByBusinessIdAndScheduledAtBetween(
            Mockito.eq(1L), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class)
        )).thenReturn(List.of(bookedAppointment));

        mockMvc.perform(get("/api/v1/appointments/availability")
                .param("businessId", "1")
                .param("serviceId", "77")
                .param("date", date.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableSlots", org.hamcrest.Matchers.hasItem("2030-06-15T10:00")));

        mockMvc.perform(get("/api/v1/appointments/availability")
                .param("businessId", "1")
                .param("serviceId", "77")
                .param("date", date.toString())
                .param("staffUserId", "44"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableSlots", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem("2030-06-15T10:00"))));
    }

    @Test
    void customerCanCancelTheirOwnUpcomingAppointment() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setId(12L);
        appointment.setBusinessId(1L);
        appointment.setCustomerId(2L);
        appointment.setServiceName("Consultation");
        appointment.setScheduledAt(LocalDateTime.now().plusDays(2));
        appointment.setDurationMinutes(30);
        appointment.setStatus(AppointmentStatus.BOOKED);

        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(9L);
        Business business = new Business();
        business.setTenantId(5L);
        BusinessUser businessUser = new BusinessUser();
        businessUser.setTenantId(5L);
        businessUser.setBusinessId(1L);
        businessUser.setUserId(44L);
        businessUser.setStatus("ACTIVE");

        Mockito.when(appointmentRepository.findById(12L)).thenReturn(Optional.of(appointment));
        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(customer));
        Mockito.when(businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(5L, 1L, "ACTIVE"))
            .thenReturn(List.of(businessUser));
        Mockito.when(appointmentRepository.save(Mockito.any(Appointment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/appointments/12/status")
                .param("status", "CANCELLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        Mockito.verify(notificationService).create(
            5L, 44L, "APPOINTMENT_CANCELLED", "Appointment cancelled",
            "Your appointment for Consultation was cancelled.", "/dashboard"
        );
    }

    @Test
    void businessCanConfirmAppointmentWithCustomerNote() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setId(13L);
        appointment.setBusinessId(1L);
        appointment.setCustomerId(2L);
        appointment.setServiceName("Consultation");
        appointment.setScheduledAt(LocalDateTime.now().plusDays(2));
        appointment.setDurationMinutes(30);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setNotes("Customer booking note");

        Business business = new Business();
        business.setTenantId(5L);
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(44L);
        Mockito.when(appointmentRepository.findById(13L)).thenReturn(Optional.of(appointment));
        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customer));
        Mockito.when(appointmentRepository.save(Mockito.any(Appointment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/appointments/13/status")
                .param("status", "CONFIRMED")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessNote\":\"Please arrive 10 minutes early.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.businessNote").value("Please arrive 10 minutes early."))
            .andExpect(jsonPath("$.notes").value("Customer booking note"));

        mockMvc.perform(put("/api/v1/appointments/13/status")
            .param("status", "CANCELLED")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessNote\":\"We need to reschedule your appointment.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.businessNote").value("We need to reschedule your appointment."))
            .andExpect(jsonPath("$.notes").value("Customer booking note"));

        Mockito.verify(notificationService).create(
            5L, 44L, "APPOINTMENT_CONFIRMED", "Appointment confirmed",
            "Your appointment for Consultation was confirmed.", "/dashboard"
        );
        Mockito.verify(notificationService).create(
            5L, 44L, "APPOINTMENT_CANCELLED", "Appointment cancelled",
            "Your appointment for Consultation was cancelled.", "/dashboard"
        );
    }

    @Test
    void customerCannotCancelAnotherCustomersAppointment() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setId(12L);
        appointment.setBusinessId(1L);
        appointment.setCustomerId(2L);
        appointment.setServiceName("Consultation");
        appointment.setScheduledAt(LocalDateTime.now().plusDays(2));
        appointment.setDurationMinutes(30);
        appointment.setStatus(AppointmentStatus.BOOKED);

        CustomerProfile otherCustomer = new CustomerProfile();
        otherCustomer.setId(4L);
        Business business = new Business();
        business.setTenantId(5L);

        Mockito.when(appointmentRepository.findById(12L)).thenReturn(Optional.of(appointment));
        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(false);
        Mockito.when(customerProfileRepository.findByUserIdAndTenantId(9L, 5L)).thenReturn(Optional.of(otherCustomer));

        mockMvc.perform(put("/api/v1/appointments/12/status")
                .param("status", "CANCELLED"))
            .andExpect(status().isForbidden());

        Mockito.verify(appointmentRepository, Mockito.never()).save(Mockito.any(Appointment.class));
    }
}
