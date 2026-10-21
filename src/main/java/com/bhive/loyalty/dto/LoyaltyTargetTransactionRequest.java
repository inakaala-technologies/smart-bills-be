package com.bhive.loyalty.dto;

import com.bhive.loyalty.entity.LoyaltyTargetType;

public record LoyaltyTargetTransactionRequest(LoyaltyTargetType targetType, Long targetId, Integer quantity,
                                              String idempotencyKey, String referenceType, Long referenceId, String note) {
}
