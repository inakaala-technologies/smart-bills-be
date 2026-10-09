package com.bhive.business.dto;

import java.math.BigDecimal;
import java.util.List;

public record BusinessInsightsResponse(
    List<TrendPoint> revenueTrend,
    List<AgingBucket> paymentAging,
    MembershipMetrics memberships,
    CustomerMetrics customers
) {
    public record TrendPoint(String label, BigDecimal amount) {}
    public record AgingBucket(String label, BigDecimal amount, long invoiceCount) {}
    public record MembershipMetrics(long active, long renewingSoon, long churned, double renewalRate) {}
    public record CustomerMetrics(long acquiredLast30Days, long repeatCustomers, double retentionRate, List<TrendPoint> acquisitionTrend) {}
}