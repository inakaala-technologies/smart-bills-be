package com.bhive.loyalty.dto;

import java.math.BigDecimal;

public record LoyaltySettingsRequest(Integer pointsPerCurrencyUnit, BigDecimal maxRedemptionPercent) {
}
