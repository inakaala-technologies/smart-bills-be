package com.bhive.appointment.controller;

import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.dto.AppointmentStatusUpdateRequest;
import com.bhive.appointment.dto.AppointmentAvailabilityResponse;
import com.bhive.appointment.dto.AppointmentStaffOption;
import com.bhive.appointment.dto.AppointmentStaffView;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.appointment.repository.AppointmentStaffRepository;
import com.bhive.appointment.service.AppointmentBillingService;
import com.bhive.appointment.entity.AppointmentStaff;
import com.bhive.billing.entity.Invoice;
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
import com.bhive.loyalty.dto.LoyaltyTransactionRequest;
import com.bhive.loyalty.entity.LoyaltyEntryType;
import com.bhive.loyalty.entity.LoyaltyTargetType;
import com.bhive.loyalty.service.LoyaltyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

    public record AppointmentStaffRequest(String name, String email, String phone) {}

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final AppointmentStaffRepository appointmentStaffRepository;
    private final BusinessMembershipService businessMembershipService;
    private final BusinessRepository businessRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final BusinessUserRepository businessUserRepository;
    private final NotificationService notificationService;
    private final LoyaltyService loyaltyService;
    private final AppointmentBillingService appointmentBillingService;

    public AppointmentController(AppointmentRepository appointmentRepository,
                                 AppointmentServiceRepository appointmentServiceRepository,
                                 AppointmentStaffRepository appointmentStaffRepository,
                                 BusinessMembershipService businessMembershipService,
                                 BusinessRepository businessRepository,
                                 CustomerProfileRepository customerProfileRepository,
                                 BusinessCustomerRepository businessCustomerRepository,
                                 BusinessUserRepository businessUserRepository,
                                 NotificationService notificationService,
                                 LoyaltyService loyaltyService,
                                 AppointmentBillingService appointmentBillingService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentServiceRepository = appointmentServiceRepository;
        this.appointmentStaffRepository = appointmentStaffRepository;
        this.businessMembershipService = businessMembershipService;
        this.businessRepository = businessRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
        this.businessUserRepository = businessUserRepository;
        this.notificationService = notificationService;
        this.loyaltyService = loyaltyService;
        this.appointmentBillingService = appointmentBillingService;
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
        return ResponseEntity.ok(attachStaffNames(appointments, tenantId));
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
            .map(this::attachStaffName)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/appointments/availability")
    public ResponseEntity<AppointmentAvailabilityResponse> getAvailability(@RequestParam Long businessId,
                                                                            @RequestParam Long serviceId,
                                                                            @RequestParam LocalDate date,
                                                                            @RequestParam(required = false) Long staffId,
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

        List<AppointmentStaff> staffMembers = activeAppointmentStaff(tenantId, businessId);
        Optional<AppointmentStaff> selectedStaff = staffId != null
            ? staffMembers.stream().filter(member -> staffId.equals(member.getId())).findFirst()
            : staffUserId == null
                ? Optional.empty()
                : staffMembers.stream().filter(member -> staffUserId.equals(member.getLinkedUserId())).findFirst();
        if ((staffId != null || staffUserId != null) && selectedStaff.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<AppointmentStaffOption> staff = staffMembers.stream().map(this::toStaffOption).toList();

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
            boolean occupied = selectedStaff.isPresent()
                ? hasResourceConflict(booked, slotStart, slotEnd, selectedStaff.get())
                : staffMembers.isEmpty()
                    ? hasResourceConflict(booked, slotStart, slotEnd, null)
                    : staffMembers.stream().allMatch(member -> hasResourceConflict(booked, slotStart, slotEnd, member));
            if (!occupied && slotStart.isAfter(LocalDateTime.now())) slots.add(slotStart.toString());
        }
        return ResponseEntity.ok(new AppointmentAvailabilityResponse(date, slots, staff));
    }

    private boolean hasResourceConflict(
        List<Appointment> booked,
        LocalDateTime slotStart,
        LocalDateTime slotEnd,
        AppointmentStaff staff
    ) {
        return booked.stream().anyMatch(appointment -> {
            LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
            boolean overlaps = appointment.getScheduledAt().isBefore(slotEnd) && appointmentEnd.isAfter(slotStart);
            if (!overlaps) return false;
            if (staff == null) return true;
            if (appointment.getStaffId() != null) return staff.getId().equals(appointment.getStaffId());
            return appointment.getStaffUserId() == null
                || staff.getLinkedUserId() != null && staff.getLinkedUserId().equals(appointment.getStaffUserId());
        });
    }

    @GetMapping("/businesses/{businessId}/appointment-staff")
    public ResponseEntity<List<AppointmentStaffView>> getBusinessAppointmentStaff(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || !canManageBusiness(userId, tenantId, businessId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(activeAppointmentStaff(tenantId, businessId).stream().map(this::toStaffView).toList());
    }

    @PostMapping("/businesses/{businessId}/appointment-staff")
    public ResponseEntity<AppointmentStaffView> addBusinessAppointmentStaff(
        @PathVariable Long businessId,
        @RequestBody(required = false) AppointmentStaffRequest request
    ) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || !canManageBusiness(userId, tenantId, businessId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String name = request == null || request.name() == null ? "" : request.name().trim();
        String email = cleanEmail(request == null ? null : request.email());
        String phone = cleanPhone(request == null ? null : request.phone());
        if (name.isEmpty() || name.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a staff name up to 160 characters.");
        }
        if (email == null && phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an email or phone number for the staff member.");
        }
        if (email != null && email.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be 255 characters or fewer.");
        }
        if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (phone != null && phone.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number must be 30 characters or fewer.");
        }

        synchronizeLegacyBusinessStaff(tenantId, businessId);
        boolean duplicate = appointmentStaffRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCaseOrderByNameAsc(
                tenantId, businessId, "ACTIVE").stream()
            .anyMatch(member -> email != null && email.equalsIgnoreCase(member.getEmail())
                || phone != null && normalizedPhone(phone).equals(normalizedPhone(member.getPhone())));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A staff member with this email or phone is already added.");
        }

        AppointmentStaff staff = new AppointmentStaff();
        staff.setTenantId(tenantId);
        staff.setBusinessId(businessId);
        staff.setName(name);
        staff.setEmail(email);
        staff.setPhone(phone);
        staff.setStatus("ACTIVE");
        return ResponseEntity.status(HttpStatus.CREATED).body(toStaffView(appointmentStaffRepository.save(staff)));
    }

    @PutMapping("/appointments/{id}/staff")
    public ResponseEntity<Appointment> updateAppointmentStaff(@PathVariable Long id,
                                                               @RequestParam(required = false) Long staffId,
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
        List<AppointmentStaff> activeStaff = activeAppointmentStaff(tenantId, appointment.getBusinessId());
        Optional<AppointmentStaff> selectedStaff = staffId != null
            ? activeStaff.stream().filter(staff -> staffId.equals(staff.getId())).findFirst()
            : staffUserId == null
                ? Optional.empty()
                : activeStaff.stream().filter(staff -> staffUserId.equals(staff.getLinkedUserId())).findFirst();
        if ((staffId != null || staffUserId != null) && selectedStaff.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (selectedStaff.isPresent()) {
            LocalDateTime appointmentEnd = appointment.getScheduledAt().plusMinutes(appointment.getDurationMinutes());
            boolean conflict = appointmentRepository.findByBusinessIdAndScheduledAtBetween(
                    appointment.getBusinessId(), appointment.getScheduledAt().toLocalDate().atStartOfDay(),
                    appointment.getScheduledAt().toLocalDate().plusDays(1).atStartOfDay()).stream()
                .filter(existing -> !existing.getId().equals(appointment.getId()))
                .filter(existing -> existing.getStatus() == AppointmentStatus.BOOKED || existing.getStatus() == AppointmentStatus.CONFIRMED)
                .anyMatch(existing -> {
                    LocalDateTime existingEnd = existing.getScheduledAt().plusMinutes(existing.getDurationMinutes());
                    return existing.getScheduledAt().isBefore(appointmentEnd) && existingEnd.isAfter(appointment.getScheduledAt())
                        && (existing.getStaffId() == null && existing.getStaffUserId() == null
                            || selectedStaff.get().getId().equals(existing.getStaffId())
                            || existing.getStaffId() == null && selectedStaff.get().getLinkedUserId() != null
                                && selectedStaff.get().getLinkedUserId().equals(existing.getStaffUserId()));
                });
            if (conflict) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        appointment.setStaffId(selectedStaff.map(AppointmentStaff::getId).orElse(null));
        appointment.setStaffUserId(selectedStaff.map(AppointmentStaff::getLinkedUserId).orElse(null));
        appointment.setStaffName(selectedStaff.map(AppointmentStaff::getName).orElse(null));
        return ResponseEntity.ok(appointmentRepository.save(appointment));
    }

    @PostMapping("/appointments")
    @Transactional
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (request == null || tenantId == null || userId == null || request.getBusinessId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.getLoyaltyPointsToRedeem() != null && request.getLoyaltyPointsToRedeem() < 0) {
            return ResponseEntity.badRequest().build();
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
        if (request.isHomeVisit()) {
            String address = request.getVisitAddress() == null ? "" : request.getVisitAddress().trim();
            if (!business.get().isHomeServicesEnabled() || address.isEmpty() || address.length() > 500) {
                return ResponseEntity.badRequest().build();
            }
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

        List<AppointmentStaff> activeStaff = activeAppointmentStaff(tenantId, request.getBusinessId());
        Long requestedStaffId = request.getStaffId();
        Long legacyRequestedStaffUserId = requestedStaffId == null ? request.getStaffUserId() : null;
        Optional<AppointmentStaff> requestedStaff = requestedStaffId != null
            ? activeStaff.stream().filter(member -> requestedStaffId.equals(member.getId())).findFirst()
            : legacyRequestedStaffUserId == null
                ? Optional.empty()
                : activeStaff.stream().filter(member -> legacyRequestedStaffUserId.equals(member.getLinkedUserId())).findFirst();
        if ((requestedStaffId != null || legacyRequestedStaffUserId != null) && requestedStaff.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        AppointmentStaff assignedStaff = requestedStaff.orElse(null);

        LocalDateTime scheduledEnd = scheduledAt.plusMinutes(selectedService.get().getDurationMinutes());
        List<Appointment> sameDayBookings = appointmentRepository.findByBusinessIdAndScheduledAtBetween(
                request.getBusinessId(), scheduledAt.toLocalDate().atStartOfDay(), scheduledAt.toLocalDate().plusDays(1).atStartOfDay())
            .stream().filter(existing -> existing.getStatus() == AppointmentStatus.BOOKED || existing.getStatus() == AppointmentStatus.CONFIRMED).toList();
        if (assignedStaff == null && !activeStaff.isEmpty()) {
            assignedStaff = activeStaff.stream()
                .filter(staff -> !hasResourceConflict(sameDayBookings, scheduledAt, scheduledEnd, staff))
                .findFirst().orElse(null);
            if (assignedStaff == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } else if (hasResourceConflict(sameDayBookings, scheduledAt, scheduledEnd, assignedStaff)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Appointment appointment = new Appointment();
        appointment.setBusinessId(request.getBusinessId());
        appointment.setCustomerId(appointmentCustomer.get().getId());
        appointment.setAppointmentServiceId(selectedService.get().getId());
        appointment.setStaffId(assignedStaff == null ? null : assignedStaff.getId());
        appointment.setStaffUserId(assignedStaff == null ? null : assignedStaff.getLinkedUserId());
        appointment.setStaffName(assignedStaff == null ? null : assignedStaff.getName());
        appointment.setServiceName(selectedService.get().getName());
        appointment.setServicePrice(selectedService.get().getPrice());
        appointment.setLoyaltyPointsRedeemed(0);
        appointment.setLoyaltyDiscountAmount(BigDecimal.ZERO);
        appointment.setScheduledAt(scheduledAt);
        appointment.setDurationMinutes(selectedService.get().getDurationMinutes());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setNotes(request.getNotes() == null ? null : request.getNotes().trim());
        appointment.setHomeVisit(request.isHomeVisit());
        appointment.setVisitingCharge(request.isHomeVisit() ? business.get().getHomeVisitCharge() : BigDecimal.ZERO);
        appointment.setVisitAddress(request.isHomeVisit() ? request.getVisitAddress().trim() : null);
        Appointment saved = appointmentRepository.save(appointment);
        int pointsToRedeem = request.getLoyaltyPointsToRedeem() == null ? 0 : request.getLoyaltyPointsToRedeem();
        if (pointsToRedeem > 0) {
            var redemption = loyaltyService.redeemForPurchase(tenantId, saved.getBusinessId(), saved.getCustomerId(),
                pointsToRedeem, saved.getServicePrice(), "APPOINTMENT:" + saved.getId(),
                "APPOINTMENT", saved.getId(), "Points applied to appointment booking");
            saved.setLoyaltyPointsRedeemed(redemption.pointsRedeemed());
            saved.setLoyaltyDiscountAmount(redemption.discountAmount());
            saved = appointmentRepository.save(saved);
        }
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
    @Transactional
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
        attachStaffName(appointment);
        appointment.setStatus(requestedStatus);
        if (businessUser && (requestedStatus == AppointmentStatus.CONFIRMED || requestedStatus == AppointmentStatus.CANCELLED)) {
            String trimmedNote = businessNote == null ? null : businessNote.trim();
            appointment.setBusinessNote(trimmedNote == null || trimmedNote.isEmpty() ? null : trimmedNote);
        }
        Appointment saved = appointmentRepository.save(appointment);
        if (previousStatus != AppointmentStatus.COMPLETED && requestedStatus == AppointmentStatus.COMPLETED
            && saved.getAppointmentServiceId() != null) {
            loyaltyService.awardTargetIfConfigured(tenantId, saved.getBusinessId(), saved.getCustomerId(),
                LoyaltyTargetType.SERVICE, saved.getAppointmentServiceId(),
                "APPOINTMENT-EARN:" + saved.getId(), "APPOINTMENT", saved.getId(),
                "Points earned for completed service");
        }
        if (previousStatus != AppointmentStatus.COMPLETED && requestedStatus == AppointmentStatus.COMPLETED) {
            appointmentBillingService.createInvoiceForCompletedAppointment(saved, tenantId, userId);
        }
        if (previousStatus != AppointmentStatus.CANCELLED && requestedStatus == AppointmentStatus.CANCELLED
            && saved.getLoyaltyPointsRedeemed() != null && saved.getLoyaltyPointsRedeemed() > 0) {
            loyaltyService.record(tenantId, saved.getBusinessId(), LoyaltyEntryType.EARN,
                new LoyaltyTransactionRequest(saved.getCustomerId(), saved.getLoyaltyPointsRedeemed(),
                    "APPOINTMENT-CANCEL:" + saved.getId(), "APPOINTMENT_CANCEL", saved.getId(),
                    "Points returned after appointment cancellation"));
        }
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

    private List<AppointmentStaffOption> appointmentStaffOptions(Long tenantId, Long businessId) {
        return activeAppointmentStaff(tenantId, businessId).stream().map(this::toStaffOption).toList();
    }

    private List<AppointmentStaff> activeAppointmentStaff(Long tenantId, Long businessId) {
        synchronizeLegacyBusinessStaff(tenantId, businessId);
        return appointmentStaffRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCaseOrderByNameAsc(
            tenantId, businessId, "ACTIVE");
    }

    private void synchronizeLegacyBusinessStaff(Long tenantId, Long businessId) {
        businessUserRepository.findByBusinessId(businessId).stream()
            .filter(member -> tenantId.equals(member.getTenantId()) && member.getUserId() != null)
            .forEach(member -> {
                if (appointmentStaffRepository.findByTenantIdAndBusinessIdAndLinkedUserId(
                    tenantId, businessId, member.getUserId()).isPresent()) {
                    return;
                }
                CustomerProfile profile = customerProfileRepository
                    .findByUserIdAndTenantId(member.getUserId(), tenantId)
                    .orElse(null);
                AppointmentStaff staff = new AppointmentStaff();
                staff.setTenantId(tenantId);
                staff.setBusinessId(businessId);
                staff.setName(profile == null || profile.getName() == null || profile.getName().isBlank()
                    ? "Staff " + member.getUserId()
                    : profile.getName());
                staff.setEmail(profile == null ? null : cleanEmail(profile.getEmail()));
                staff.setPhone(profile == null ? null : cleanPhone(profile.getPhone()));
                staff.setLinkedUserId(member.getUserId());
                staff.setStatus(member.getStatus() == null ? "ACTIVE" : member.getStatus());
                appointmentStaffRepository.save(staff);
            });
    }

    private AppointmentStaffOption toStaffOption(AppointmentStaff staff) {
        return new AppointmentStaffOption(staff.getId(), staff.getName(), staff.getLinkedUserId());
    }

    private Appointment attachStaffName(Appointment appointment) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) attachStaffNames(List.of(appointment), tenantId);
        return appointment;
    }

    private List<Appointment> attachStaffNames(List<Appointment> appointments, Long tenantId) {
        Map<Long, List<AppointmentStaff>> staffByBusiness = new HashMap<>();
        Set<Long> synchronizedBusinesses = new HashSet<>();
        for (Appointment appointment : appointments) {
            Long businessId = appointment.getBusinessId();
            if (businessId == null || appointment.getStaffId() == null && appointment.getStaffUserId() == null) {
                continue;
            }
            if (appointment.getStaffId() == null && synchronizedBusinesses.add(businessId)) {
                synchronizeLegacyBusinessStaff(tenantId, businessId);
            }
            List<AppointmentStaff> businessStaff = staffByBusiness.computeIfAbsent(
                businessId, id -> appointmentStaffRepository.findByTenantIdAndBusinessId(tenantId, id));
            businessStaff.stream()
                .filter(staff -> appointment.getStaffId() != null
                    ? appointment.getStaffId().equals(staff.getId())
                    : appointment.getStaffUserId().equals(staff.getLinkedUserId()))
                .findFirst()
                .ifPresent(staff -> appointment.setStaffName(staff.getName()));
        }
        return appointments;
    }

    private AppointmentStaffView toStaffView(AppointmentStaff staff) {
        return new AppointmentStaffView(staff.getId(), staff.getName(), staff.getEmail(), staff.getPhone(),
            staff.getLinkedUserId());
    }

    private String cleanEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return phone.trim();
    }

    private String normalizedPhone(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s()-]", "");
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