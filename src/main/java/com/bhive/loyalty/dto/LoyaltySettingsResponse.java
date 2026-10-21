package com.bhive.loyalty.dto;

import com.bhive.loyalty.entity.LoyaltyProgramSettings;
import java.math.BigDecimal;

public record LoyaltySettingsResponse(Long businessId, boolean configured, Integer pointsPerCurrencyUnit,
                                      BigDecimal maxRedemptionPercent) {
    public static LoyaltySettingsResponse unconfigured(Long businessId) {
        return new LoyaltySettingsResponse(businessId, false, null, null);
    }

    public static LoyaltySettingsResponse from(LoyaltyProgramSettings settings) {
        return new LoyaltySettingsResponse(settings.getBusinessId(), true,
            settings.getPointsPerCurrencyUnit(), settings.getMaxRedemptionPercent());
    }
}
