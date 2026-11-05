package com.bhive.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.bhive.billing.entity.InvoiceDiscountType;
import com.bhive.billing.entity.InvoiceStatus;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Size;

public record InvoiceCreateRequest(
    @NotNull @Positive Long businessId,
    @NotNull @Positive Long customerId,
    @NotNull InvoiceStatus status,
    LocalDate dueDate,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal subtotal,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal gstAmount,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal totalAmount,
    @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal invoiceDiscountAmount,
    InvoiceDiscountType invoiceDiscountType,
    @DecimalMin("0.00")     @Digits(integer = 8, fraction = 2) BigDecimal invoiceDiscountValue,
    @Size(max = 100) List<@Valid InvoiceLineItemRequest> items
) {
}