package com.bhive.billing.dto;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceDiscountType;
import com.bhive.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record InvoiceResponse(
    Long id,
    Long businessId,
    Long customerId,
    String invoiceNumber,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal subtotal,
    BigDecimal gstAmount,
    BigDecimal totalAmount,
    BigDecimal invoiceDiscountAmount,
    InvoiceDiscountType invoiceDiscountType,
    BigDecimal invoiceDiscountValue,
    Integer loyaltyPointsRedeemed,
    BigDecimal loyaltyDiscountAmount,
    InvoiceStatus status,
    String createdAt,
    String updatedAt,
    List<InvoiceLineItemView> items
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(), invoice.getBusinessId(), invoice.getCustomerId(), invoice.getInvoiceNumber(),
            invoice.getIssueDate(), invoice.getDueDate(), invoice.getSubtotal(), invoice.getGstAmount(),
            invoice.getTotalAmount(), invoice.getInvoiceDiscountAmount(), invoice.getInvoiceDiscountType(),
            invoice.getInvoiceDiscountValue(), invoice.getLoyaltyPointsRedeemed(), invoice.getLoyaltyDiscountAmount(),
            invoice.getStatus(),
            toUtcTimestamp(invoice.getCreatedAt()),
            toUtcTimestamp(invoice.getUpdatedAt()),
            invoice.getLineItems().stream().map(InvoiceLineItemView::from).toList()
        );
    }

    private static String toUtcTimestamp(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }
}