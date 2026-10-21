package com.bhive.loyalty.dto;

public record LoyaltyTransactionRequest(Long customerId, Integer points, String idempotencyKey,
                                        String referenceType, Long referenceId, String note) {
}
