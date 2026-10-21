package com.bhive.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltyRedemptionResponse(Integer pointsRedeemed, BigDecimal discountAmount, long balanceAfter) {
}
