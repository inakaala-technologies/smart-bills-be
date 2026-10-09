package com.bhive.business.controller;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.dto.BusinessInsightsResponse;
import com.bhive.business.dto.BusinessInsightsResponse.AgingBucket;
import com.bhive.business.dto.BusinessInsightsResponse.CustomerMetrics;
import com.bhive.business.dto.BusinessInsightsResponse.MembershipMetrics;
import com.bhive.business.dto.BusinessInsightsResponse.TrendPoint;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessInsightsController {

    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final BusinessCustomerRepository businessCustomerRepository;

    public BusinessInsightsController(BusinessRepository businessRepository,
                                      BusinessUserRepository businessUserRepository,
                                      InvoiceRepository invoiceRepository,
                                      PaymentRepository paymentRepository,
                                      MembershipRepository membershipRepository,
                                      BusinessCustomerRepository businessCustomerRepository) {
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.membershipRepository = membershipRepository;
        this.businessCustomerRepository = businessCustomerRepository;
    }

    @GetMapping("/{businessId}/insights")
    public ResponseEntity<BusinessInsightsResponse> getInsights(@PathVariable Long businessId,
                                                                @RequestParam(defaultValue = "6") int months) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || months < 3 || months > 24) {
            return ResponseEntity.badRequest().build();
        }
        boolean authorized = businessRepository.findById(businessId)
            .filter(business -> tenantId.equals(business.getTenantId()))
            .isPresent() && businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE")
            .stream().anyMatch(member -> userId.equals(member.getUserId()));
        if (!authorized) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        List<Invoice> invoices = invoiceRepository.findByBusinessId(businessId);
        List<Payment> payments = paymentRepository.findByBusinessId(businessId);
        List<Membership> memberships = membershipRepository.findByBusinessIdAndTenantId(businessId, tenantId);
        List<BusinessCustomer> customers = businessCustomerRepository.findByTenantIdAndBusinessId(tenantId, businessId);
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        List<TrendPoint> revenueTrend = new ArrayList<>();
        List<TrendPoint> acquisitionTrend = new ArrayList<>();
        for (int offset = months - 1; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            BigDecimal revenue = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.VERIFIED && payment.getPaymentDate() != null
                    && YearMonth.from(payment.getPaymentDate()).equals(month))
                .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            long acquired = customers.stream().filter(customer -> customer.getCreatedAt() != null
                && YearMonth.from(customer.getCreatedAt()).equals(month)).count();
            revenueTrend.add(new TrendPoint(month.toString(), revenue));
            acquisitionTrend.add(new TrendPoint(month.toString(), BigDecimal.valueOf(acquired)));
        }

        Map<String, BigDecimal> agingTotals = new HashMap<>(Map.of(
            "Not due", BigDecimal.ZERO, "1-30 days", BigDecimal.ZERO,
            "31-60 days", BigDecimal.ZERO, "61+ days", BigDecimal.ZERO
        ));
        Map<String, Long> agingCounts = new HashMap<>();
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == null || invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.DRAFT
                || invoice.getDueDate() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            String bucket = daysOverdue <= 0 ? "Not due" : daysOverdue <= 30 ? "1-30 days" : daysOverdue <= 60 ? "31-60 days" : "61+ days";
            agingTotals.merge(bucket, invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount(), BigDecimal::add);
            agingCounts.merge(bucket, 1L, Long::sum);
        }
        List<AgingBucket> paymentAging = agingTotals.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> List.of("Not due", "1-30 days", "31-60 days", "61+ days").indexOf(entry.getKey())))
            .map(entry -> new AgingBucket(entry.getKey(), entry.getValue(), agingCounts.getOrDefault(entry.getKey(), 0L)))
            .toList();

        long activeMembers = memberships.stream().filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
            .filter(membership -> membership.getEndDate() == null || !membership.getEndDate().isBefore(today)).count();
        long renewingSoon = memberships.stream().filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
            .filter(membership -> membership.getEndDate() != null && !membership.getEndDate().isBefore(today)
                && !membership.getEndDate().isAfter(today.plusDays(30))).count();
        long renewed = memberships.stream().filter(membership -> membership.getStatus() == MembershipStatus.RENEWED
            || hasSubsequentMembership(membership, memberships)).count();
        long churned = memberships.stream().filter(membership -> membership.getStatus() == MembershipStatus.EXPIRED
            || membership.getStatus() == MembershipStatus.CANCELLED)
            .filter(membership -> !hasSubsequentMembership(membership, memberships)).count();
        long renewalBase = renewed + churned;
        double renewalRate = renewalBase == 0 ? 0 : (double) renewed / renewalBase;

        Map<Long, Long> invoicesPerCustomer = invoices.stream().filter(invoice -> invoice.getCustomerId() != null)
            .collect(Collectors.groupingBy(Invoice::getCustomerId, Collectors.counting()));
        long repeatCustomers = invoicesPerCustomer.values().stream().filter(count -> count > 1).count();
        long acquiredLast30Days = customers.stream().filter(customer -> customer.getCreatedAt() != null
            && customer.getCreatedAt().toLocalDate().isAfter(today.minusDays(30))).count();
        long customerCount = customers.stream().map(BusinessCustomer::getCustomerProfileId).filter(Objects::nonNull).distinct().count();
        double retentionRate = customerCount == 0 ? 0 : (double) repeatCustomers / customerCount;

        return ResponseEntity.ok(new BusinessInsightsResponse(
            revenueTrend, paymentAging,
            new MembershipMetrics(activeMembers, renewingSoon, churned, renewalRate),
            new CustomerMetrics(acquiredLast30Days, repeatCustomers, retentionRate, acquisitionTrend)
        ));
    }

    private boolean hasSubsequentMembership(Membership membership, List<Membership> memberships) {
        if (membership.getCustomerId() == null || membership.getEndDate() == null) return false;
        return memberships.stream()
            .filter(candidate -> candidate != membership)
            .filter(candidate -> membership.getCustomerId().equals(candidate.getCustomerId()))
            .anyMatch(candidate -> candidate.getStartDate() != null
                && candidate.getStartDate().isAfter(membership.getEndDate()));
    }
}