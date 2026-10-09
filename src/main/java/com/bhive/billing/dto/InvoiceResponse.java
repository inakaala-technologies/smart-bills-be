package com.bhive.billing.dto;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    InvoiceStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(), invoice.getBusinessId(), invoice.getCustomerId(), invoice.getInvoiceNumber(),
            invoice.getIssueDate(), invoice.getDueDate(), invoice.getSubtotal(), invoice.getGstAmount(),
            invoice.getTotalAmount(), invoice.getStatus(), invoice.getCreatedAt(), invoice.getUpdatedAt()
        );
    }
}