package com.bhive.loyalty.dto;

import com.bhive.loyalty.entity.LoyaltyTargetType;

public record LoyaltyRuleRequest(LoyaltyTargetType targetType, Long targetId, Integer points, boolean active) {
}
