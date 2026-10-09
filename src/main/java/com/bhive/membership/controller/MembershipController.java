package com.bhive.membership.controller;

import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.notification.service.NotificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class MembershipController {

    private final MembershipRepository membershipRepository;
    private final BusinessMembershipService businessMembershipService;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public MembershipController(MembershipRepository membershipRepository,
                               BusinessMembershipService businessMembershipService,
                               CustomerProfileRepository customerProfileRepository,
                               NotificationService notificationService) {
        this.membershipRepository = membershipRepository;
        this.businessMembershipService = businessMembershipService;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/memberships")
    public List<Membership> getAllMemberships() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        return membershipRepository.findAll().stream()
            .filter(membership -> tenantId == null || tenantId.equals(membership.getTenantId()))
            .filter(membership -> userId == null || membership.getBusinessId() == null || businessMembershipService.userHasAccessToBusiness(userId, membership.getBusinessId(), tenantId))
            .toList();
    }

    @GetMapping("/memberships/{id}")
    public ResponseEntity<Membership> getMembershipById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        return membershipRepository.findById(id)
            .filter(membership -> tenantId == null || tenantId.equals(membership.getTenantId()))
            .filter(membership -> userId == null || membership.getBusinessId() == null || businessMembershipService.userHasAccessToBusiness(userId, membership.getBusinessId(), tenantId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/memberships")
    public ResponseEntity<Membership> createMembership(@RequestBody Membership membership) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (membership == null) {
            return ResponseEntity.badRequest().build();
        }

        if (membership.getTenantId() == null || membership.getBusinessId() == null || membership.getCustomerId() == null) {
            return ResponseEntity.badRequest().build();
        }

        if (tenantId != null && !tenantId.equals(membership.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (membership.getPlanName() == null || membership.getPlanName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (membership.getStartDate() == null) {
            return ResponseEntity.badRequest().build();
        }

        if (membership.getEndDate() != null && membership.getEndDate().isBefore(membership.getStartDate())) {
            return ResponseEntity.badRequest().build();
        }

        if (membership.getBusinessId() != null && (userId == null || tenantId == null || !businessMembershipService.userHasAccessToBusiness(userId, membership.getBusinessId(), tenantId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (membership.getStatus() == null) {
            membership.setStatus(MembershipStatus.PENDING);
        }

        Membership saved = membershipRepository.save(membership);
        customerProfileRepository.findById(saved.getCustomerId())
            .filter(customer -> saved.getTenantId().equals(customer.getTenantId()))
            .map(CustomerProfile::getUserId)
            .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
            .ifPresent(recipientUserId -> notificationService.create(
                saved.getTenantId(),
                recipientUserId,
                "MEMBERSHIP_CREATED",
                "Membership created",
                "Your " + saved.getPlanName() + " membership was created.",
                "/dashboard"
            ));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
