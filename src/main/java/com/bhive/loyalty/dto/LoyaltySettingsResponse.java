package com.bhive.loyalty.dto;

import com.bhive.loyalty.entity.LoyaltyProgramSettings;
import com.bhive.loyalty.entity.InvoiceEarningMode;
import java.math.BigDecimal;

public record LoyaltySettingsResponse(Long businessId, boolean configured, Integer pointsPerCurrencyUnit,
                                      BigDecimal maxRedemptionPercent, InvoiceEarningMode invoiceEarningMode,
                                      BigDecimal invoiceEarningValue) {
    public static LoyaltySettingsResponse unconfigured(Long businessId) {
        return new LoyaltySettingsResponse(businessId, false, null, null, null, null);
    }

    public static LoyaltySettingsResponse from(LoyaltyProgramSettings settings) {
        return new LoyaltySettingsResponse(settings.getBusinessId(), true,
            settings.getPointsPerCurrencyUnit(), settings.getMaxRedemptionPercent(),
            settings.getInvoiceEarningMode(), settings.getInvoiceEarningValue());
    }
}
