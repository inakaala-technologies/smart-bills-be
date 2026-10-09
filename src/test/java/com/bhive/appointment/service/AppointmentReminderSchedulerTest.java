package com.bhive.appointment.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderSchedulerTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private AppointmentReminderScheduler scheduler;

    @Test
    void sendsAndMarksUpcomingReminder() {
        Appointment appointment = new Appointment();
        appointment.setId(4L);
        appointment.setBusinessId(7L);
        appointment.setCustomerId(12L);
        appointment.setServiceName("Consultation");
        appointment.setScheduledAt(LocalDateTime.now().plusHours(23).plusMinutes(50));
        Business business = new Business();
        business.setTenantId(3L);
        CustomerProfile customer = new CustomerProfile();
        customer.setTenantId(3L);
        customer.setUserId(15L);
        when(appointmentRepository.findByReminder24hSentAtIsNullAndScheduledAtBetweenAndStatusIn(
            any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()
        )).thenReturn(List.of(appointment));
        when(appointmentRepository.findByReminder1hSentAtIsNullAndScheduledAtBetweenAndStatusIn(
            any(LocalDateTime.class), any(LocalDateTime.class), anyCollection()
        )).thenReturn(List.of());
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business));
        when(customerProfileRepository.findById(12L)).thenReturn(Optional.of(customer));

        scheduler.sendUpcomingAppointmentReminders();

        assertNotNull(appointment.getReminder24hSentAt());
        verify(notificationService).create(
            eq(3L), eq(15L), eq("APPOINTMENT_REMINDER"), eq("Appointment tomorrow"),
            contains("Consultation is scheduled for"), eq("/dashboard")
        );
        verify(appointmentRepository).save(appointment);
    }
}