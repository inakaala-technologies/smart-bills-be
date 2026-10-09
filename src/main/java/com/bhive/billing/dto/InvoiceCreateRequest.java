package com.bhive.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.bhive.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceCreateRequest(
    @NotNull @Positive Long businessId,
    @NotNull @Positive Long customerId,
    @NotNull InvoiceStatus status,
    LocalDate dueDate,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal subtotal,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal gstAmount,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal totalAmount
) {
}