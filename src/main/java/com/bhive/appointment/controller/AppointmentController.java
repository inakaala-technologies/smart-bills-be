package com.bhive.appointment.controller;

import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.dto.AppointmentStatusUpdateRequest;
import com.bhive.appointment.dto.AppointmentAvailabilityResponse;
import com.bhive.appointment.dto.AppointmentStaffOption;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final BusinessMembershipService businessMembershipService;
    private final BusinessRepository businessRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final BusinessUserRepository businessUserRepository;
    private final NotificationService notificationService;

    public AppointmentController(AppointmentRepository appointmentRepository,
                                 AppointmentServiceRepository appointmentServiceRepository,
                                 BusinessMembershipService businessMembershipService,
                                 BusinessRepository businessRepository,
                                 CustomerProfileRepository customerProfileRepository,
                                 BusinessCustomerRepository businessCustomerRepository,
                                 BusinessUserRepository businessUserRepository,
                                 NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentServiceRepository = appointmentServiceRepository;
        this.businessMembershipService = businessMembershipService;
        this.businessRepository = businessRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
        this.businessUserRepository = businessUserRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<Appointment>> getAllAppointments() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long customerId = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(CustomerProfile::getId)
            .orElse(null);
        List<Appointment> appointments = appointmentRepository.findAll().stream()
            .filter(appointment -> belongsToTenant(appointment, tenantId))
            .filter(appointment -> canManageBusiness(userId, tenantId, appointment.getBusinessId())
                || (customerId != null && customerId.equals(appointment.getCustomerId())))
            .toList();
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return appointmentRepository.findById(id)
            .filter(appointment -> canAccessAppointment(appointment, userId, tenantId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/appointments/availability")
    public ResponseEntity<AppointmentAvailabilityResponse> getAvailability(@RequestParam Long businessId,
                                                                            @RequestParam Long serviceId,
                                                                            @RequestParam LocalDate date,
                                                                            @RequestParam(required = false) Long staffUserId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Optional<Business> business = businessRepository.findById(businessId)
            .filter(candidate -> tenantId.equals(candidate.getTenantId()))
            .filter(Business::isAppointmentsEnabled);
        Optional<AppointmentService> selectedService = appointmentServiceRepository.findById(serviceId)
            .filter(service -> tenantId.equals(service.getTenantId()))
            .filter(service -> businessId.equals(service.getBusinessId()))
            .filter(AppointmentService::isEnabled);
        Optional<CustomerProfile> customer = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId);
        boolean businessUser = canManageBusiness(userId, tenantId, businessId);
        if (business.isEmpty() || selectedService.isEmpty()
            || (!businessUser && (customer.isEmpty() || !isCustomerLinkedToBusiness(tenantId, businessId, customer.get().getId())))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<AppointmentStaffOption> staff = businessUserRepository
            .findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE").stream()
            .filter(member -> member.getUserId() != null)
            .map(member -> new AppointmentStaffOption(member.getUserId(), "Staff " + member.getUserId()))
            .toList();
        if (staffUserId != null && staff.stream().noneMatch(option -> staffUserId.equals(option.userId()))) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> booked = appointmentRepository.findByBusinessIdAndScheduledAtBetween(businessId, dayStart, dayEnd).stream()
            .filter(appointment -> appointment.getStatus() == AppointmentStatus.BOOKED || appointment.getStatus() == AppointmentStatus.CONFIRMED)
            .toList();
        int duration = selectedService.get().getDurationMinutes();
        LocalTime opensAt = LocalTime.of(9, 0);
        LocalTime closesAt = LocalTime.of(17, 0);
        List<String> slots = new ArrayList<>();
        for (LocalTime start = opensAt; !start.plusMinutes(duration).isAfter(closesAt); start = start.plusMinutes(30)) {
            LocalDateTime slotStart = date.atTime(start);
            LocalDateTime slotEnd = slotStart.plusMinutes(duration);
            boolean occupied = staffUserId != null
                ? hasResourceConflict(booked, slotStart, slotEnd, staffUserId)
                : staff.isEmpty()
                    ? hasResourceConflict(booked, slotStart, slotEnd, null)
                    : staff.stream().allMatch(option -> hasResourceConflict(booked, slotStart, slotEnd, option.userId()));
            if (!occupied && slotStart.isAfter(LocalDateTime.now())) slots.add(slotStart.toString());
        }
        return ResponseEntity.ok(new AppointmentAvailabilityResponse(date, slots, staff));
    }

    private boolean hasResourceConflict(List<Appointment> booked, LocalDateTime slotStart, LocalDateTime slotEnd, Long staffUserId) {
        return booked.stream().anyMatch(appointment -> {
            LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
            boolean overlaps = appointment.getScheduledAt().isBefore(slotEnd) && appointmentEnd.isAfter(slotStart);
            return overlaps && (staffUserId == null || appointment.getStaffUserId() == null
                || staffUserId.equals(appointment.getStaffUserId()));
        });
    }

    @GetMapping("/businesses/{businessId}/appointment-staff")
    public ResponseEntity<List<AppointmentStaffOption>> getBusinessAppointmentStaff(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || !canManageBusiness(userId, tenantId, businessId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<AppointmentStaffOption> staff = businessUserRepository
            .findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE").stream()
            .filter(member -> member.getUserId() != null)
            .map(member -> new AppointmentStaffOption(member.getUserId(), "Staff " + member.getUserId()))
            .toList();
        return ResponseEntity.ok(staff);
    }

    @PutMapping("/appointments/{id}/staff")
    public ResponseEntity<Appointment> updateAppointmentStaff(@PathVariable Long id,
                                                               @RequestParam(required = false) Long staffUserId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Optional<Appointment> result = appointmentRepository.findById(id)
            .filter(appointment -> belongsToTenant(appointment, tenantId))
            .filter(appointment -> canManageBusiness(userId, tenantId, appointment.getBusinessId()));
        if (result.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Appointment appointment = result.get();
        if (appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return ResponseEntity.badRequest().build();
        }
        if (staffUserId != null && businessUserRepository
            .findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, appointment.getBusinessId(), "ACTIVE").stream()
            .noneMatch(member -> staffUserId.equals(member.getUserId()))) {
            return ResponseEntity.badRequest().build();
        }
        if (staffUserId != null) {
            LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
            boolean conflict = appointmentRepository.findByBusinessIdAndScheduledAtBetween(
                    appointment.getBusinessId(), appointment.getScheduledAt().toLocalDate().atStartOfDay(),
                    appointment.getScheduledAt().toLocalDate().plusDays(1).atStartOfDay()).stream()
                .filter(existing -> !existing.getId().equals(appointment.getId()))
                .filter(existing -> existing.getStatus() == AppointmentStatus.BOOKED || existing.getStatus() == AppointmentStatus.CONFIRMED)
                .anyMatch(existing -> {
                    LocalDateTime existingEnd = existing.getScheduledAt().plusMinutes(existing.getDurationMinutes());
                    return existing.getScheduledAt().isBefore(appointmentEnd) && existingEnd.isAfter(appointment.getScheduledAt())
                        && (existing.getStaffUserId() == null || staffUserId.equals(existing.getStaffUserId()));
                });
            if (conflict) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        appointment.setStaffUserId(staffUserId);
        return ResponseEntity.ok(appointmentRepository.save(appointment));
    }

    @PostMapping("/appointments")
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || request.getBusinessId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        LocalDateTime scheduledAt = request.getScheduledAt();
        if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now()) || request.getAppointmentServiceId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Business> business = businessRepository.findById(request.getBusinessId())
            .filter(candidate -> tenantId.equals(candidate.getTenantId()));
        if (business.isEmpty() || !business.get().isAppointmentsEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<AppointmentService> selectedService = appointmentServiceRepository.findById(request.getAppointmentServiceId())
            .filter(service -> tenantId.equals(service.getTenantId()))
            .filter(service -> request.getBusinessId().equals(service.getBusinessId()))
            .filter(AppointmentService::isEnabled);
        if (selectedService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (scheduledAt.toLocalTime().isBefore(LocalTime.of(9, 0))
            || scheduledAt.toLocalTime().plusMinutes(selectedService.get().getDurationMinutes()).isAfter(LocalTime.of(17, 0))) {
            return ResponseEntity.badRequest().build();
        }

        boolean businessUser = canManageBusiness(userId, tenantId, request.getBusinessId());
        Optional<CustomerProfile> appointmentCustomer;
        if (businessUser && request.getCustomerId() != null) {
            appointmentCustomer = customerProfileRepository.findById(request.getCustomerId())
                .filter(candidate -> tenantId.equals(candidate.getTenantId()));
        } else {
            appointmentCustomer = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId);
        }

        if (appointmentCustomer.isEmpty() || !isCustomerLinkedToBusiness(
            tenantId, request.getBusinessId(), appointmentCustomer.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<BusinessUser> activeStaff = businessUserRepository
            .findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, request.getBusinessId(), "ACTIVE").stream()
            .filter(member -> member.getUserId() != null)
            .toList();
        Long requestedStaffId = request.getStaffUserId();
        if (requestedStaffId != null && activeStaff.stream().noneMatch(member -> requestedStaffId.equals(member.getUserId()))) {
            return ResponseEntity.badRequest().build();
        }
        Long assignedStaffId = requestedStaffId;

        LocalDateTime scheduledEnd = scheduledAt.plusMinutes(selectedService.get().getDurationMinutes());
        List<Appointment> sameDayBookings = appointmentRepository.findByBusinessIdAndScheduledAtBetween(
                request.getBusinessId(), scheduledAt.toLocalDate().atStartOfDay(), scheduledAt.toLocalDate().plusDays(1).atStartOfDay())
            .stream().filter(existing -> existing.getStatus() == AppointmentStatus.BOOKED || existing.getStatus() == AppointmentStatus.CONFIRMED).toList();
        if (assignedStaffId == null && !activeStaff.isEmpty()) {
            assignedStaffId = activeStaff.stream().map(BusinessUser::getUserId)
                .filter(staffId -> !hasResourceConflict(sameDayBookings, scheduledAt, scheduledEnd, staffId))
                .findFirst().orElse(null);
            if (assignedStaffId == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } else if (hasResourceConflict(sameDayBookings, scheduledAt, scheduledEnd, assignedStaffId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Appointment appointment = new Appointment();
        appointment.setBusinessId(request.getBusinessId());
        appointment.setCustomerId(appointmentCustomer.get().getId());
        appointment.setAppointmentServiceId(selectedService.get().getId());
        appointment.setStaffUserId(assignedStaffId);
        appointment.setServiceName(selectedService.get().getName());
        appointment.setServicePrice(selectedService.get().getPrice());
        appointment.setScheduledAt(scheduledAt);
        appointment.setDurationMinutes(selectedService.get().getDurationMinutes());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setNotes(request.getNotes() == null ? null : request.getNotes().trim());
        Appointment saved = appointmentRepository.save(appointment);
        String businessName = business.get().getName() == null ? "the business" : business.get().getName();
        String bookingMessage = "An appointment for " + saved.getServiceName() + " was booked with " + businessName + ".";
        if (businessUser) {
            notifyCustomer(tenantId, appointmentCustomer.get().getUserId(), userId,
                "APPOINTMENT_BOOKED", "Appointment scheduled", bookingMessage);
        } else {
            notifyBusinessUsers(tenantId, saved.getBusinessId(), userId,
                "APPOINTMENT_BOOKED", "New appointment", bookingMessage);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/appointments/{id}/status")
    public ResponseEntity<Appointment> updateAppointmentStatus(@PathVariable Long id,
                                                               @RequestParam AppointmentStatus status,
                                                               @RequestBody(required = false) AppointmentStatusUpdateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return appointmentRepository.findById(id)
            .filter(appointment -> belongsToTenant(appointment, tenantId))
            .map(appointment -> updateStatus(appointment, status, userId, tenantId, request))
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private ResponseEntity<Appointment> updateStatus(Appointment appointment,
                                                     AppointmentStatus requestedStatus,
                                                     Long userId,
                                                     Long tenantId,
                                                     AppointmentStatusUpdateRequest request) {
        boolean businessUser = canManageBusiness(userId, tenantId, appointment.getBusinessId());
        Long customerId = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(CustomerProfile::getId)
            .orElse(null);
        boolean appointmentCustomer = customerId != null && customerId.equals(appointment.getCustomerId());

        if (!businessUser) {
            if (!appointmentCustomer || requestedStatus != AppointmentStatus.CANCELLED
                || (appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.CONFIRMED)
                || !appointment.getScheduledAt().isAfter(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else if (!isValidBusinessTransition(appointment.getStatus(), requestedStatus)) {
            return ResponseEntity.badRequest().build();
        }

        String businessNote = request == null ? null : request.getBusinessNote();
        if (businessUser && businessNote != null && businessNote.length() > 1000) {
            return ResponseEntity.badRequest().build();
        }
        AppointmentStatus previousStatus = appointment.getStatus();
        appointment.setStatus(requestedStatus);
        if (businessUser && (requestedStatus == AppointmentStatus.CONFIRMED || requestedStatus == AppointmentStatus.CANCELLED)) {
            String trimmedNote = businessNote == null ? null : businessNote.trim();
            appointment.setBusinessNote(trimmedNote == null || trimmedNote.isEmpty() ? null : trimmedNote);
        }
        Appointment saved = appointmentRepository.save(appointment);
        if (previousStatus != requestedStatus) {
            String statusLabel = requestedStatus.name().toLowerCase().replace('_', ' ');
            String message = "Your appointment for " + saved.getServiceName() + " was " + statusLabel + ".";
            if (businessUser) {
                customerProfileRepository.findById(saved.getCustomerId())
                    .filter(customer -> tenantId.equals(customer.getTenantId()))
                    .map(CustomerProfile::getUserId)
                    .ifPresent(recipientUserId -> notifyCustomer(
                        tenantId, recipientUserId, userId, "APPOINTMENT_" + requestedStatus.name(),
                        "Appointment " + statusLabel, message
                    ));
            } else {
                notifyBusinessUsers(tenantId, saved.getBusinessId(), userId,
                    "APPOINTMENT_" + requestedStatus.name(), "Appointment " + statusLabel, message);
            }
        }
        return ResponseEntity.ok(saved);
    }

    private void notifyCustomer(Long tenantId, Long recipientUserId, Long actorUserId,
                                String type, String title, String message) {
        if (recipientUserId == null || recipientUserId.equals(actorUserId)) return;
        notificationService.create(tenantId, recipientUserId, type, title, message, "/dashboard");
    }

    private void notifyBusinessUsers(Long tenantId, Long businessId, Long actorUserId,
                                     String type, String title, String message) {
        businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE")
            .stream()
            .map(com.bhive.business.entity.BusinessUser::getUserId)
            .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(actorUserId))
            .distinct()
            .forEach(recipientUserId -> notificationService.create(
                tenantId, recipientUserId, type, title, message, "/dashboard"
            ));
    }

    private boolean canAccessAppointment(Appointment appointment, Long userId, Long tenantId) {
        if (!belongsToTenant(appointment, tenantId)) {
            return false;
        }
        if (canManageBusiness(userId, tenantId, appointment.getBusinessId())) {
            return true;
        }
        return customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(customer -> customer.getId().equals(appointment.getCustomerId()))
            .orElse(false);
    }

    private boolean belongsToTenant(Appointment appointment, Long tenantId) {
        return appointment.getBusinessId() != null && businessRepository.findById(appointment.getBusinessId())
            .map(business -> tenantId.equals(business.getTenantId()))
            .orElse(false);
    }

    private boolean canManageBusiness(Long userId, Long tenantId, Long businessId) {
        return businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId);
    }

    private boolean isCustomerLinkedToBusiness(Long tenantId, Long businessId, Long customerId) {
        return businessCustomerRepository.findByTenantIdAndBusinessId(tenantId, businessId).stream()
            .anyMatch(link -> customerId.equals(link.getCustomerProfileId()) && "ACTIVE".equalsIgnoreCase(link.getStatus()));
    }

    private boolean isValidBusinessTransition(AppointmentStatus current, AppointmentStatus requested) {
        if (current == requested) {
            return true;
        }
        return switch (current) {
            case BOOKED -> requested == AppointmentStatus.CONFIRMED || requested == AppointmentStatus.CANCELLED;
            case CONFIRMED -> requested == AppointmentStatus.COMPLETED || requested == AppointmentStatus.CANCELLED || requested == AppointmentStatus.NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}