package com.bhive.bills.service;

public record ScheduledBillFetch(
        Long savedBillId,
        Long customerId,
        Long tenantId,
        String providerCode,
        String categoryCode,
        String providerInputsJson,
        String claimToken
) {
}
