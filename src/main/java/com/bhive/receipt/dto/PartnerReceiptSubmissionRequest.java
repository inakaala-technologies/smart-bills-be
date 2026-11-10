package com.bhive.receipt.dto;

import java.math.BigDecimal;
import java.util.List;

public record PartnerReceiptSubmissionRequest(
    String externalReceiptId,
    String receiptNumber,
    String customerLinkToken,
    String customerEmail,
    String customerPhone,
    String retailerReference,
    String outletReference,
    String transactionAt,
    String currency,
    BigDecimal totalAmount,
    List<ReceiptLineItem> items
) {
}
