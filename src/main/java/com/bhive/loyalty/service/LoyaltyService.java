package com.bhive.loyalty.service;

import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.billing.repository.ProductRepository;
import com.bhive.loyalty.dto.LoyaltyTransactionRequest;
import com.bhive.loyalty.dto.LoyaltyTargetTransactionRequest;
import com.bhive.loyalty.dto.LoyaltyRedemptionResponse;
import com.bhive.loyalty.entity.LoyaltyProgramSettings;
import com.bhive.loyalty.entity.InvoiceEarningMode;
import com.bhive.loyalty.entity.LoyaltyEntryType;
import com.bhive.loyalty.entity.LoyaltyLedgerEntry;
import com.bhive.loyalty.entity.LoyaltyRule;
import com.bhive.loyalty.entity.LoyaltyTargetType;
import com.bhive.loyalty.repository.LoyaltyLedgerRepository;
import com.bhive.loyalty.repository.LoyaltyRuleRepository;
import com.bhive.loyalty.repository.LoyaltyProgramSettingsRepository;
import com.bhive.membership.repository.MembershipPlanRepository;
import com.bhive.customer.repository.BusinessCustomerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoyaltyService {
    private final LoyaltyLedgerRepository ledgerRepository;
    private final LoyaltyRuleRepository ruleRepository;
    private final LoyaltyProgramSettingsRepository settingsRepository;
    private final BusinessCustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AppointmentServiceRepository serviceRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public LoyaltyService(LoyaltyLedgerRepository ledgerRepository, LoyaltyRuleRepository ruleRepository,
                          LoyaltyProgramSettingsRepository settingsRepository,
                          BusinessCustomerRepository customerRepository, ProductRepository productRepository,
                          AppointmentServiceRepository serviceRepository, MembershipPlanRepository membershipPlanRepository) {
        this.ledgerRepository = ledgerRepository;
        this.ruleRepository = ruleRepository;
        this.settingsRepository = settingsRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.serviceRepository = serviceRepository;
        this.membershipPlanRepository = membershipPlanRepository;
    }

    public void validateRuleTarget(Long tenantId, Long businessId, LoyaltyTargetType type, Long targetId) {
        boolean valid = switch (type) {
            case PRODUCT -> productRepository.findById(targetId).filter(item -> businessId.equals(item.getBusinessId())).isPresent();
            case SERVICE -> serviceRepository.findById(targetId).filter(item -> tenantId.equals(item.getTenantId()) && businessId.equals(item.getBusinessId())).isPresent();
            case MEMBERSHIP -> membershipPlanRepository.findByIdAndBusinessIdAndTenantId(targetId, businessId, tenantId).isPresent();
        };
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected loyalty target does not belong to this business.");
    }

    @Transactional
    public LoyaltyLedgerEntry record(Long tenantId, Long businessId, LoyaltyEntryType type, LoyaltyTransactionRequest request) {
        if (request == null || request.customerId() == null || request.points() == null || request.points() <= 0
            || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer, positive points, and idempotencyKey are required.");
        }
        customerRepository.findByBusinessIdAndCustomerProfileId(businessId, request.customerId())
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is not linked to this business."));

        Optional<LoyaltyLedgerEntry> existing = ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(
            businessId, request.customerId(), request.idempotencyKey());
        if (existing.isPresent()) {
            LoyaltyLedgerEntry entry = existing.get();
            if (entry.getEntryType() != type || !entry.getPoints().equals(request.points())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different points transaction.");
            }
            return entry;
        }

        long balance = ledgerRepository.getBalance(tenantId, businessId, request.customerId());
        if (type == LoyaltyEntryType.REDEEM && balance < request.points()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer does not have enough loyalty points.");
        }

        LoyaltyLedgerEntry entry = new LoyaltyLedgerEntry();
        entry.setTenantId(tenantId);
        entry.setBusinessId(businessId);
        entry.setCustomerId(request.customerId());
        entry.setEntryType(type);
        entry.setPoints(request.points());
        entry.setIdempotencyKey(request.idempotencyKey());
        entry.setReferenceType(request.referenceType());
        entry.setReferenceId(request.referenceId());
        entry.setNote(request.note());
        return ledgerRepository.save(entry);
    }

    @Transactional
    public LoyaltyLedgerEntry awardForTarget(Long tenantId, Long businessId, Long customerId,
                                             LoyaltyTargetTransactionRequest request) {
        if (request == null || request.targetType() == null || request.targetId() == null
            || request.quantity() == null || request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target and positive quantity are required.");
        }
        validateRuleTarget(tenantId, businessId, request.targetType(), request.targetId());
        LoyaltyRule rule = ruleRepository.findByTenantIdAndBusinessIdAndTargetTypeAndTargetId(
                tenantId, businessId, request.targetType(), request.targetId())
            .filter(LoyaltyRule::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active loyalty rule exists for this target."));
        long points = (long) rule.getPoints() * request.quantity();
        if (points > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Awarded points exceed the supported limit.");
        }
        return record(tenantId, businessId, LoyaltyEntryType.EARN,
            new LoyaltyTransactionRequest(customerId, (int) points, request.idempotencyKey(),
                request.referenceType(), request.referenceId(), request.note()));
    }

    @Transactional
    public void awardTargetIfConfigured(Long tenantId, Long businessId, Long customerId,
                                        LoyaltyTargetType targetType, Long targetId,
                                        String idempotencyKey, String referenceType, Long referenceId,
                                        String note) {
        Optional<LoyaltyRule> ruleResult = ruleRepository.findByTenantIdAndBusinessIdAndTargetTypeAndTargetId(
            tenantId, businessId, targetType, targetId).filter(LoyaltyRule::isActive);
        if (ruleResult.isEmpty()) return;
        record(tenantId, businessId, LoyaltyEntryType.EARN,
            new LoyaltyTransactionRequest(customerId, ruleResult.get().getPoints(), idempotencyKey,
                referenceType, referenceId, note));
    }

    @Transactional
    public LoyaltyRedemptionResponse redeemForPurchase(Long tenantId, Long businessId, Long customerId,
                                                        int points, BigDecimal purchaseAmount,
                                                        String idempotencyKey, String referenceType,
                                                        Long referenceId, String note) {
        if (customerId == null || points <= 0 || purchaseAmount == null || purchaseAmount.signum() <= 0
            || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Customer, positive points, purchase amount, and idempotencyKey are required.");
        }
        customerRepository.findForUpdate(tenantId, businessId, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is not linked to this business."));

        Optional<LoyaltyLedgerEntry> existing = ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(
            businessId, customerId, idempotencyKey);
        if (existing.isPresent()) {
            LoyaltyLedgerEntry entry = existing.get();
            if (entry.getEntryType() != LoyaltyEntryType.REDEEM || entry.getPoints() != points) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different points transaction.");
            }
            long currentBalance = ledgerRepository.getBalance(tenantId, businessId, customerId);
            BigDecimal discount = BigDecimal.valueOf(points)
                .divide(BigDecimal.valueOf(getConfiguredSettings(tenantId, businessId).getPointsPerCurrencyUnit()), 2, RoundingMode.DOWN);
            return new LoyaltyRedemptionResponse(points, discount, currentBalance);
        }

        LoyaltyProgramSettings settings = getConfiguredSettings(tenantId, businessId);
        long balance = ledgerRepository.getBalance(tenantId, businessId, customerId);
        if (points > balance) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer does not have enough loyalty points.");
        }
        BigDecimal discount = BigDecimal.valueOf(points)
            .divide(BigDecimal.valueOf(settings.getPointsPerCurrencyUnit()), 2, RoundingMode.DOWN);
        BigDecimal maximumDiscount = purchaseAmount.multiply(settings.getMaxRedemptionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        if (discount.signum() <= 0 || discount.compareTo(maximumDiscount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Requested points exceed this business's maximum redemption limit for this purchase.");
        }

        LoyaltyLedgerEntry entry = new LoyaltyLedgerEntry();
        entry.setTenantId(tenantId);
        entry.setBusinessId(businessId);
        entry.setCustomerId(customerId);
        entry.setEntryType(LoyaltyEntryType.REDEEM);
        entry.setPoints(points);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setNote(note);
        ledgerRepository.save(entry);
        return new LoyaltyRedemptionResponse(points, discount, balance - points);
    }

    @Transactional
    public LoyaltyRedemptionResponse redeemMaximumForPurchase(Long tenantId, Long businessId, Long customerId,
                                                              BigDecimal purchaseAmount, String idempotencyKey,
                                                              String referenceType, Long referenceId, String note) {
        if (customerId == null || purchaseAmount == null || purchaseAmount.signum() <= 0) {
            return new LoyaltyRedemptionResponse(0, BigDecimal.ZERO, 0);
        }
        Optional<LoyaltyProgramSettings> settingsResult = settingsRepository.findByTenantIdAndBusinessId(tenantId, businessId);
        if (settingsResult.isEmpty() || settingsResult.get().getPointsPerCurrencyUnit() == null
            || settingsResult.get().getMaxRedemptionPercent() == null) {
            return new LoyaltyRedemptionResponse(0, BigDecimal.ZERO,
                ledgerRepository.getBalance(tenantId, businessId, customerId));
        }
        customerRepository.findForUpdate(tenantId, businessId, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is not linked to this business."));
        long balance = ledgerRepository.getBalance(tenantId, businessId, customerId);
        LoyaltyProgramSettings settings = settingsResult.get();
        BigDecimal maximumDiscount = purchaseAmount.multiply(settings.getMaxRedemptionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        long pointsAllowedByCap = maximumDiscount.multiply(BigDecimal.valueOf(settings.getPointsPerCurrencyUnit()))
            .setScale(0, RoundingMode.DOWN).longValue();
        int points = (int) Math.min(Math.min(balance, pointsAllowedByCap), Integer.MAX_VALUE);
        if (points <= 0) {
            return new LoyaltyRedemptionResponse(0, BigDecimal.ZERO, balance);
        }
        return redeemForPurchase(tenantId, businessId, customerId, points, purchaseAmount, idempotencyKey,
            referenceType, referenceId, note);
    }

    @Transactional
    public void awardInvoiceIfConfigured(Long tenantId, Long businessId, Long customerId,
                                         Long invoiceId, BigDecimal finalAmount) {
        if (customerId == null || invoiceId == null || finalAmount == null || finalAmount.signum() <= 0) return;
        Optional<LoyaltyProgramSettings> settingsResult = settingsRepository.findByTenantIdAndBusinessId(tenantId, businessId);
        if (settingsResult.isEmpty()) return;
        LoyaltyProgramSettings settings = settingsResult.get();
        if (settings.getInvoiceEarningMode() == null || settings.getInvoiceEarningValue() == null) return;
        BigDecimal pointsValue = settings.getInvoiceEarningMode() == InvoiceEarningMode.FIXED_POINTS
            ? settings.getInvoiceEarningValue()
            : finalAmount.multiply(settings.getInvoiceEarningValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        BigDecimal wholePoints = pointsValue.setScale(0, RoundingMode.DOWN);
        int points = wholePoints.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0
            ? Integer.MAX_VALUE
            : wholePoints.intValue();
        if (points <= 0) return;
        record(tenantId, businessId, LoyaltyEntryType.EARN,
            new LoyaltyTransactionRequest(customerId, points, "INVOICE-EARN:" + invoiceId,
                "INVOICE", invoiceId, "Points earned for paid invoice " + invoiceId));
    }

    private LoyaltyProgramSettings getConfiguredSettings(Long tenantId, Long businessId) {
        return settingsRepository.findByTenantIdAndBusinessId(tenantId, businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "This business has not configured loyalty redemption settings."));
    }
}
