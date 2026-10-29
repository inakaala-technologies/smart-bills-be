package com.bhive.loyalty.dto;

import java.math.BigDecimal;
import com.bhive.loyalty.entity.InvoiceEarningMode;

public record LoyaltySettingsRequest(Integer pointsPerCurrencyUnit, BigDecimal maxRedemptionPercent,
                                     InvoiceEarningMode invoiceEarningMode, BigDecimal invoiceEarningValue) {
}
