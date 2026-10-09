package com.bhive.payment.dto;

import com.bhive.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentCreateRequest(
    @NotNull Long invoiceId,
    @NotNull Long businessId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull PaymentMethod paymentMethod
) {
}