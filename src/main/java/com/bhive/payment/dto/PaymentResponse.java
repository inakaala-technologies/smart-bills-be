package com.bhive.payment.dto;

import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentMethod;
import com.bhive.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    Long invoiceId,
    Long businessId,
    Long customerId,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    PaymentStatus status,
    String referenceNumber,
    LocalDateTime paymentDate
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getId(), payment.getInvoiceId(), payment.getBusinessId(), payment.getCustomerId(),
            payment.getAmount(), payment.getPaymentMethod(), payment.getStatus(), payment.getReferenceNumber(),
            payment.getPaymentDate()
        );
    }
}