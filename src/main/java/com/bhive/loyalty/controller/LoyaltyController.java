package com.bhive.loyalty.controller;

import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.loyalty.dto.LoyaltyBalanceResponse;
import com.bhive.loyalty.dto.LoyaltyCustomerBalanceResponse;
import com.bhive.loyalty.dto.LoyaltyRuleRequest;
import com.bhive.loyalty.dto.LoyaltyTransactionRequest;
import com.bhive.loyalty.dto.LoyaltyTargetTransactionRequest;
import com.bhive.loyalty.dto.LoyaltySettingsRequest;
import com.bhive.loyalty.dto.LoyaltySettingsResponse;
import com.bhive.loyalty.entity.LoyaltyEntryType;
import com.bhive.loyalty.entity.InvoiceEarningMode;
import com.bhive.loyalty.entity.LoyaltyLedgerEntry;
import com.bhive.loyalty.entity.LoyaltyRule;
import com.bhive.loyalty.repository.LoyaltyLedgerRepository;
import com.bhive.loyalty.repository.LoyaltyProgramSettingsRepository;
import com.bhive.loyalty.repository.LoyaltyRuleRepository;
import com.bhive.loyalty.service.LoyaltyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/loyalty")
public class LoyaltyController {
    private final LoyaltyRuleRepository ruleRepository;
    private final LoyaltyLedgerRepository ledgerRepository;
    private final LoyaltyProgramSettingsRepository settingsRepository;
    private final LoyaltyService loyaltyService;
    private final BusinessMembershipService businessMembershipService;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;

    public LoyaltyController(LoyaltyRuleRepository ruleRepository, LoyaltyLedgerRepository ledgerRepository,
                             LoyaltyProgramSettingsRepository settingsRepository, LoyaltyService loyaltyService,
                             BusinessMembershipService businessMembershipService,
                             CustomerProfileRepository customerProfileRepository,
                             BusinessCustomerRepository businessCustomerRepository) {
        this.ruleRepository = ruleRepository;
        this.ledgerRepository = ledgerRepository;
        this.settingsRepository = settingsRepository;
        this.loyaltyService = loyaltyService;
        this.businessMembershipService = businessMembershipService;
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
    }

    @GetMapping("/settings")
    public ResponseEntity<LoyaltySettingsResponse> getSettings(@PathVariable Long businessId) {
        if (!canManage(businessId) && !customerCanAccessBusiness(businessId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(settingsRepository.findByTenantIdAndBusinessId(tenantId(), businessId)
            .map(LoyaltySettingsResponse::from)
            .orElseGet(() -> LoyaltySettingsResponse.unconfigured(businessId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<LoyaltySettingsResponse> saveSettings(@PathVariable Long businessId,
                                                                 @RequestBody LoyaltySettingsRequest request) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.pointsPerCurrencyUnit() == null || request.pointsPerCurrencyUnit() <= 0
            || request.maxRedemptionPercent() == null || request.maxRedemptionPercent().signum() <= 0
            || request.maxRedemptionPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0
            || request.maxRedemptionPercent().scale() > 2
            || (request.invoiceEarningMode() == null) != (request.invoiceEarningValue() == null)
            || (request.invoiceEarningMode() != null && (request.invoiceEarningValue().signum() <= 0
                || request.invoiceEarningValue().scale() > 2
                || (request.invoiceEarningMode() == InvoiceEarningMode.PERCENTAGE
                    && request.invoiceEarningValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0)
                || (request.invoiceEarningMode() == InvoiceEarningMode.FIXED_POINTS
                    && (request.invoiceEarningValue().stripTrailingZeros().scale() > 0
                        || request.invoiceEarningValue().compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) > 0))))) {
            return ResponseEntity.badRequest().build();
        }
        var settings = settingsRepository.findByTenantIdAndBusinessId(tenantId(), businessId)
            .orElseGet(com.bhive.loyalty.entity.LoyaltyProgramSettings::new);
        settings.setTenantId(tenantId());
        settings.setBusinessId(businessId);
        settings.setPointsPerCurrencyUnit(request.pointsPerCurrencyUnit());
        settings.setMaxRedemptionPercent(request.maxRedemptionPercent());
        settings.setInvoiceEarningMode(request.invoiceEarningMode());
        settings.setInvoiceEarningValue(request.invoiceEarningValue());
        return ResponseEntity.ok(LoyaltySettingsResponse.from(settingsRepository.save(settings)));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<LoyaltyRule>> getRules(@PathVariable Long businessId) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(ruleRepository.findByTenantIdAndBusinessIdOrderByTargetTypeAscTargetIdAsc(tenantId(), businessId));
    }

    @PostMapping("/rules")
    public ResponseEntity<LoyaltyRule> createRule(@PathVariable Long businessId, @RequestBody LoyaltyRuleRequest request) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.targetType() == null || request.targetId() == null || request.points() == null || request.points() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        loyaltyService.validateRuleTarget(tenantId(), businessId, request.targetType(), request.targetId());
        if (ruleRepository.findByTenantIdAndBusinessIdAndTargetTypeAndTargetId(tenantId(), businessId, request.targetType(), request.targetId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        LoyaltyRule rule = new LoyaltyRule();
        apply(rule, businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleRepository.save(rule));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<LoyaltyRule> updateRule(@PathVariable Long businessId, @PathVariable Long ruleId,
                                                   @RequestBody LoyaltyRuleRequest request) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.targetType() == null || request.targetId() == null || request.points() == null || request.points() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        loyaltyService.validateRuleTarget(tenantId(), businessId, request.targetType(), request.targetId());
        return ruleRepository.findByIdAndTenantIdAndBusinessId(ruleId, tenantId(), businessId)
            .map(rule -> { apply(rule, businessId, request); return ResponseEntity.ok(ruleRepository.save(rule)); })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/{customerId}/balance")
    public ResponseEntity<LoyaltyBalanceResponse> getBalance(@PathVariable Long businessId, @PathVariable Long customerId) {
        if (!canManage(businessId) && !customerCanView(customerId, businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        long balance = ledgerRepository.getBalance(tenantId(), businessId, customerId);
        return ResponseEntity.ok(new LoyaltyBalanceResponse(businessId, customerId, balance));
    }

    @GetMapping("/balances")
    public ResponseEntity<List<LoyaltyCustomerBalanceResponse>> getCustomerBalances(@PathVariable Long businessId) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(ledgerRepository.getBalances(tenantId(), businessId));
    }

    @PostMapping("/customers/{customerId}/earn")
    @Transactional
    public ResponseEntity<LoyaltyLedgerEntry> earn(@PathVariable Long businessId, @PathVariable Long customerId,
                                                    @RequestBody LoyaltyTransactionRequest request) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        LoyaltyTransactionRequest normalized = new LoyaltyTransactionRequest(customerId,
            request == null ? null : request.points(), request == null ? null : request.idempotencyKey(),
            request == null ? null : request.referenceType(), request == null ? null : request.referenceId(),
            request == null ? null : request.note());
        return ResponseEntity.ok(loyaltyService.record(tenantId(), businessId, LoyaltyEntryType.EARN, normalized));
    }

    @PostMapping("/customers/{customerId}/earn-for-target")
    @Transactional
    public ResponseEntity<LoyaltyLedgerEntry> earnForTarget(@PathVariable Long businessId, @PathVariable Long customerId,
                                                             @RequestBody LoyaltyTargetTransactionRequest request) {
        if (!canManage(businessId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(loyaltyService.awardForTarget(tenantId(), businessId, customerId, request));
    }

    private void apply(LoyaltyRule rule, Long businessId, LoyaltyRuleRequest request) {
        rule.setTenantId(tenantId());
        rule.setBusinessId(businessId);
        rule.setTargetType(request.targetType());
        rule.setTargetId(request.targetId());
        rule.setPoints(request.points());
        rule.setActive(request.active());
    }

    private boolean canManage(Long businessId) {
        return tenantId() != null && userId() != null
            && businessMembershipService.userHasAccessToBusiness(userId(), businessId, tenantId());
    }

    private boolean customerCanView(Long customerId, Long businessId) {
        return tenantId() != null && userId() != null
            && customerProfileRepository.findById(customerId)
                .filter(profile -> tenantId().equals(profile.getTenantId()) && userId().equals(profile.getUserId()))
                .filter(profile -> businessCustomerRepository.findByBusinessIdAndCustomerProfileId(businessId, profile.getId())
                    .filter(link -> tenantId().equals(link.getTenantId()) && "ACTIVE".equalsIgnoreCase(link.getStatus()))
                    .isPresent())
                .isPresent();
    }

    private boolean customerCanAccessBusiness(Long businessId) {
        if (tenantId() == null || userId() == null) return false;
        return customerProfileRepository.findByUserIdAndTenantId(userId(), tenantId())
            .filter(profile -> businessCustomerRepository.findByBusinessIdAndCustomerProfileId(businessId, profile.getId())
                .filter(link -> tenantId().equals(link.getTenantId()) && "ACTIVE".equalsIgnoreCase(link.getStatus()))
                .isPresent())
            .isPresent();
    }

    private Long tenantId() { return TenantContext.getTenantId(); }
    private Long userId() { return TenantContext.getUserId(); }
}
