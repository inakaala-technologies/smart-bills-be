package com.bhive.appointment.service;

import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AppointmentReminderScheduler {

    private static final List<AppointmentStatus> ACTIVE_STATUSES = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED);
    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public AppointmentReminderScheduler(AppointmentRepository appointmentRepository,
                                        BusinessRepository businessRepository,
                                        CustomerProfileRepository customerProfileRepository,
                                        NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.businessRepository = businessRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sendUpcomingAppointmentReminders() {
        LocalDateTime now = LocalDateTime.now();
        sendReminders(
            appointmentRepository.findByReminder24hSentAtIsNullAndScheduledAtBetweenAndStatusIn(
                now.plusHours(23).plusMinutes(45), now.plusHours(24), ACTIVE_STATUSES
            ), true, now
        );
        sendReminders(
            appointmentRepository.findByReminder1hSentAtIsNullAndScheduledAtBetweenAndStatusIn(
                now.plusMinutes(45), now.plusHours(1), ACTIVE_STATUSES
            ), false, now
        );
    }

    private void sendReminders(List<Appointment> appointments, boolean dayAhead, LocalDateTime sentAt) {
        for (Appointment appointment : appointments) {
            Business business = businessRepository.findById(appointment.getBusinessId()).orElse(null);
            if (business == null) continue;
            customerProfileRepository.findById(appointment.getCustomerId())
                .filter(customer -> business.getTenantId().equals(customer.getTenantId()))
                .map(customer -> customer.getUserId())
                .filter(userId -> userId != null)
                .ifPresent(userId -> notificationService.create(
                    business.getTenantId(), userId,
                    "APPOINTMENT_REMINDER",
                    dayAhead ? "Appointment tomorrow" : "Appointment in one hour",
                    appointment.getServiceName() + " is scheduled for " + appointment.getScheduledAt().toString().replace('T', ' ') + ".",
                    "/dashboard"
                ));
            if (dayAhead) appointment.setReminder24hSentAt(sentAt);
            else appointment.setReminder1hSentAt(sentAt);
            appointmentRepository.save(appointment);
        }
    }
}