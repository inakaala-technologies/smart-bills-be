package com.bhive.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InvoiceLineItemRequest(
    @NotBlank @Size(max = 255) String productName,
    @NotNull @Positive @Digits(integer = 8, fraction = 3) BigDecimal quantity,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal gstRate
) {
}
