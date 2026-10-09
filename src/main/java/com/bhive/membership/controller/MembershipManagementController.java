package com.bhive.membership.controller;

import com.bhive.business.entity.Business;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.ProfileIdGenerator;
import com.bhive.common.util.TenantContext;
import com.bhive.notification.service.NotificationService;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.dto.CreateMembershipRequest;
import com.bhive.membership.dto.MemberUserSummary;
import com.bhive.membership.dto.MembershipReminderRequest;
import com.bhive.membership.dto.UpdateMembershipRequest;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipNotification;
import com.bhive.membership.entity.MembershipPlan;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipNotificationRepository;
import com.bhive.membership.repository.MembershipPlanRepository;
import com.bhive.membership.repository.MembershipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MembershipManagementController {

    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipNotificationRepository notificationRepository;
    private final BusinessMembershipService businessMembershipService;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final com.bhive.business.repository.BusinessRepository businessRepository;
    private final NotificationService notificationService;

    public MembershipManagementController(MembershipPlanRepository planRepository,
                                          MembershipRepository membershipRepository,
                                          MembershipNotificationRepository notificationRepository,
                                          BusinessMembershipService businessMembershipService,
                                          BusinessCustomerRepository businessCustomerRepository,
                                          CustomerProfileRepository customerProfileRepository,
                                          com.bhive.business.repository.BusinessRepository businessRepository,
                                          NotificationService notificationService) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.notificationRepository = notificationRepository;
        this.businessMembershipService = businessMembershipService;
        this.businessCustomerRepository = businessCustomerRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessRepository = businessRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/businesses/{businessId}/membership-plans")
    public ResponseEntity<List<MembershipPlan>> getPlans(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(planRepository.findByBusinessIdAndTenantIdOrderByNameAsc(businessId, tenantId));
    }

    @PostMapping("/businesses/{businessId}/membership-plans")
    public ResponseEntity<MembershipPlan> createPlan(@PathVariable Long businessId, @RequestBody MembershipPlan request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!isValidPlan(request)) return ResponseEntity.badRequest().build();

        MembershipPlan plan = new MembershipPlan();
        applyPlanChanges(plan, request);
        plan.setBusinessId(businessId);
        plan.setTenantId(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(planRepository.save(plan));
    }

    @PutMapping("/businesses/{businessId}/membership-plans/{planId}")
    public ResponseEntity<MembershipPlan> updatePlan(@PathVariable Long businessId,
                                                      @PathVariable Long planId,
                                                      @RequestBody MembershipPlan request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!isValidPlan(request)) return ResponseEntity.badRequest().build();
        return planRepository.findByIdAndBusinessIdAndTenantId(planId, businessId, tenantId)
            .map(plan -> {
                applyPlanChanges(plan, request);
                return ResponseEntity.ok(planRepository.save(plan));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/businesses/{businessId}/member-search")
    public ResponseEntity<List<MemberUserSummary>> searchMembers(@PathVariable Long businessId, @RequestParam String q) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (query.length() < 2) return ResponseEntity.ok(List.of());
        List<MemberUserSummary> matches = customerProfileRepository.findAll().stream()
            .filter(profile -> profile.getUserId() != null)
            .filter(profile -> profile.getProfileId() != null)
            .filter(profile -> profile.getProfileId() != null && profile.getProfileId().toLowerCase(Locale.ROOT).contains(query)
                || profile.getEmail() != null && profile.getEmail().toLowerCase(Locale.ROOT).contains(query)
                || profile.getPhone() != null && profile.getPhone().contains(query)
                || profile.getName() != null && profile.getName().toLowerCase(Locale.ROOT).contains(query))
            .map(profile -> new MemberUserSummary(profile.getId(), profile.getProfileId(), profile.getName(), profile.getEmail(), maskPhone(profile.getPhone())))
            .limit(20)
            .toList();
        return ResponseEntity.ok(matches);
    }

    @PostMapping("/businesses/{businessId}/memberships")
    @Transactional
    public ResponseEntity<Membership> createSubscription(@PathVariable Long businessId, @RequestBody CreateMembershipRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.getPlanId() == null || request.getStartDate() == null) return ResponseEntity.badRequest().build();

        Optional<MembershipPlan> planResult = planRepository.findByIdAndBusinessIdAndTenantId(request.getPlanId(), businessId, tenantId);
        if (planResult.isEmpty() || !planResult.get().isActive()) return ResponseEntity.badRequest().build();
        MembershipPlan plan = planResult.get();
        if (plan.getMaxMembers() != null && membershipRepository.findByBusinessId(businessId).stream()
            .filter(membership -> plan.getId().equals(membership.getPlanId()))
            .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE || membership.getStatus() == MembershipStatus.PENDING)
            .count() >= plan.getMaxMembers()) return ResponseEntity.status(HttpStatus.CONFLICT).build();

        String paymentStatus = normalizePaymentStatus(request.getPaymentStatus());
        if (paymentStatus == null) return ResponseEntity.badRequest().build();
        CustomerProfile profile = resolveMember(request, tenantId);
        if (profile == null) return ResponseEntity.badRequest().build();
        ensureBusinessCustomer(businessId, tenantId, profile.getId());

        Membership membership = new Membership();
        membership.setTenantId(tenantId);
        membership.setBusinessId(businessId);
        membership.setCustomerId(profile.getId());
        membership.setPlanId(plan.getId());
        membership.setPlanName(plan.getName());
        membership.setPrice(plan.getPrice());
        membership.setPaymentStatus(paymentStatus);
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(request.getStartDate().plusMonths(plan.getDurationMonths()));
        membership.setStatus("PENDING".equals(paymentStatus) ? MembershipStatus.PENDING : MembershipStatus.ACTIVE);
        Membership saved = membershipRepository.save(membership);
        Long recipientUserId = profile.getUserId();
        if (recipientUserId != null && !recipientUserId.equals(userId)) {
            notificationService.create(
                tenantId,
                recipientUserId,
                "MEMBERSHIP_CREATED",
                "Membership created",
                "Your " + saved.getPlanName() + " membership is " + saved.getStatus().name().toLowerCase(Locale.ROOT) + ".",
                "/dashboard"
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/businesses/{businessId}/memberships/{membershipId}")
    @Transactional
    public ResponseEntity<Membership> updateSubscription(@PathVariable Long businessId,
                                                         @PathVariable Long membershipId,
                                                         @RequestBody UpdateMembershipRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (!canManageBusiness(businessId, userId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.getPlanId() == null || request.getStartDate() == null || request.getStatus() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) return ResponseEntity.badRequest().build();

        String paymentStatus = normalizePaymentStatus(request.getPaymentStatus());
        if (paymentStatus == null) return ResponseEntity.badRequest().build();
        Optional<MembershipPlan> planResult = planRepository.findByIdAndBusinessIdAndTenantId(request.getPlanId(), businessId, tenantId);
        if (planResult.isEmpty()) return ResponseEntity.badRequest().build();

        Optional<Membership> membershipResult = membershipRepository.findByIdAndBusinessIdAndTenantId(membershipId, businessId, tenantId);
        if (membershipResult.isEmpty()) return ResponseEntity.notFound().build();

        Membership membership = membershipResult.get();
        MembershipPlan plan = planResult.get();
        if (!plan.isActive() && !plan.getId().equals(membership.getPlanId())) return ResponseEntity.badRequest().build();
        if (plan.getMaxMembers() != null && !plan.getId().equals(membership.getPlanId())
            && (request.getStatus() == MembershipStatus.ACTIVE || request.getStatus() == MembershipStatus.PENDING)) {
            long activeMembers = membershipRepository.findByBusinessIdAndTenantId(businessId, tenantId).stream()
                .filter(item -> !membershipId.equals(item.getId()))
                .filter(item -> plan.getId().equals(item.getPlanId()))
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE || item.getStatus() == MembershipStatus.PENDING)
                .count();
            if (activeMembers >= plan.getMaxMembers()) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        membership.setPlanId(plan.getId());
        membership.setPlanName(plan.getName());
        membership.setPrice(plan.getPrice());
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(request.getEndDate() == null ? request.getStartDate().plusMonths(plan.getDurationMonths()) : request.getEndDate());
        membership.setPaymentStatus(paymentStatus);
        membership.setStatus(request.getStatus());
        return ResponseEntity.ok(membershipRepository.save(membership));
    }

    @GetMapping("/memberships/my")
    public ResponseEntity<List<Membership>> getMyMemberships() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Optional<CustomerProfile> profile = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId);
        if (profile.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(membershipRepository.findByCustomerId(profile.get().getId()).stream()
            .filter(membership -> tenantId.equals(membership.getTenantId()))
            .toList());
    }

    @PostMapping("/memberships/{membershipId}/reminders")
    public ResponseEntity<MembershipNotification> sendReminder(@PathVariable Long membershipId,
                                                                @RequestBody(required = false) MembershipReminderRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Optional<Membership> found = membershipRepository.findById(membershipId)
            .filter(membership -> tenantId.equals(membership.getTenantId()))
            .filter(membership -> businessMembershipService.userHasAccessToBusiness(userId, membership.getBusinessId(), tenantId));
        if (found.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Membership membership = found.get();
        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            message = "Your " + membership.getPlanName() + " membership is due for renewal on " + membership.getEndDate() + ". Contact the business to renew.";
        }
        if (message.length() > 1000) return ResponseEntity.badRequest().build();
        List<MembershipNotification> recent = notificationRepository.findByBusinessIdAndMembershipIdOrderBySentAtDesc(membership.getBusinessId(), membershipId);
        if (!recent.isEmpty() && recent.get(0).getSentAt().isAfter(LocalDateTime.now().minusHours(24))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String notificationMessage = message.trim();
        MembershipNotification notification = new MembershipNotification();
        notification.setTenantId(tenantId);
        notification.setBusinessId(membership.getBusinessId());
        notification.setMembershipId(membershipId);
        notification.setCustomerId(membership.getCustomerId());
        notification.setMessage(notificationMessage);
        MembershipNotification savedNotification = notificationRepository.save(notification);
        customerProfileRepository.findById(membership.getCustomerId())
            .filter(profile -> tenantId.equals(profile.getTenantId()))
            .map(CustomerProfile::getUserId)
            .filter(recipientUserId -> recipientUserId != null)
            .ifPresent(recipientUserId -> notificationService.create(
                tenantId,
                recipientUserId,
                "MEMBERSHIP_REMINDER",
                "Membership reminder",
                notificationMessage,
                "/dashboard"
            ));
        return ResponseEntity.status(HttpStatus.CREATED).body(savedNotification);
    }

    @GetMapping("/membership-notifications/my")
    public ResponseEntity<List<MembershipNotification>> getMyNotifications() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Optional<CustomerProfile> profile = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId);
        if (profile.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(notificationRepository.findByCustomerIdOrderBySentAtDesc(profile.get().getId()).stream()
            .filter(notification -> tenantId.equals(notification.getTenantId()))
            .toList());
    }

    private boolean canManageBusiness(Long businessId, Long userId, Long tenantId) {
        if (businessId == null || userId == null || tenantId == null) return false;
        boolean inTenant = businessRepository.findById(businessId).map(Business::getTenantId).filter(tenantId::equals).isPresent();
        return inTenant && businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId);
    }

    private boolean isValidPlan(MembershipPlan plan) {
        return plan != null && plan.getName() != null && !plan.getName().isBlank()
            && plan.getName().length() <= 120 && plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) >= 0
            && plan.getDurationMonths() != null && plan.getDurationMonths() >= 1 && plan.getDurationMonths() <= 120
            && plan.getBillingFrequency() != null && List.of("ONE_TIME", "MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY", "CUSTOM")
                .contains(plan.getBillingFrequency().toUpperCase(Locale.ROOT));
    }

    private void applyPlanChanges(MembershipPlan target, MembershipPlan source) {
        target.setName(source.getName().trim());
        target.setDescription(source.getDescription() == null ? null : source.getDescription().trim());
        target.setPrice(source.getPrice());
        target.setBillingFrequency(source.getBillingFrequency().toUpperCase(Locale.ROOT));
        target.setDurationMonths(source.getDurationMonths());
        target.setBenefits(source.getBenefits());
        target.setMaxMembers(source.getMaxMembers());
        target.setActive(source.isActive());
    }

    private String normalizePaymentStatus(String paymentStatus) {
        String normalized = paymentStatus == null ? "PENDING" : paymentStatus.trim().toUpperCase(Locale.ROOT);
        return List.of("PAID", "PENDING", "FREE").contains(normalized) ? normalized : null;
    }

    private CustomerProfile resolveMember(CreateMembershipRequest request, Long tenantId) {
        CustomerProfile matched = null;
        if (request.getProfileId() != null && !request.getProfileId().isBlank()) {
            matched = customerProfileRepository.findAll().stream()
                .filter(profile -> request.getProfileId().equalsIgnoreCase(profile.getProfileId()))
                .filter(profile -> profile.getUserId() != null)
                .findFirst().orElse(null);
            if (matched == null) return null;
            if (tenantId.equals(matched.getTenantId())) return matched;
            CustomerProfile tenantProfile = new CustomerProfile();
            tenantProfile.setTenantId(tenantId);
            tenantProfile.setUserId(matched.getUserId());
            tenantProfile.setName(matched.getName());
            tenantProfile.setEmail(matched.getEmail());
            tenantProfile.setPhone(matched.getPhone());
            tenantProfile.setProfileId(ProfileIdGenerator.nextCustomerId(customerProfileRepository::existsByProfileId));
            return customerProfileRepository.save(tenantProfile);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            matched = customerProfileRepository.findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNull(request.getEmail().trim(), tenantId).orElse(null);
        }
        if (matched == null && request.getPhone() != null && !request.getPhone().isBlank()) {
            matched = customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNull(request.getPhone().trim(), tenantId).orElse(null);
        }
        if (matched != null) return matched;
        if (request.getMemberName() == null || request.getMemberName().isBlank() || request.getPhone() == null || request.getPhone().isBlank()) return null;

        CustomerProfile profile = new CustomerProfile();
        profile.setTenantId(tenantId);
        profile.setName(request.getMemberName().trim());
        profile.setEmail(request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT));
        profile.setPhone(request.getPhone().trim());
        profile.setProfileId(ProfileIdGenerator.nextCustomerId(customerProfileRepository::existsByProfileId));
        return customerProfileRepository.save(profile);
    }

    private void ensureBusinessCustomer(Long businessId, Long tenantId, Long customerId) {
        if (businessCustomerRepository.findByBusinessIdAndCustomerProfileId(businessId, customerId).isPresent()) return;
        BusinessCustomer link = new BusinessCustomer();
        link.setBusinessId(businessId);
        link.setTenantId(tenantId);
        link.setCustomerProfileId(customerId);
        link.setStatus("ACTIVE");
        businessCustomerRepository.save(link);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return "•••••" + phone.substring(phone.length() - 4);
    }
}