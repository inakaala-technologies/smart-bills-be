package com.bhive.receipt.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerReceiptResponse(
    Long id,
    String receiptNumber,
    String retailerName,
    String outletName,
    Instant transactionAt,
    String currency,
    BigDecimal totalAmount,
    List<ReceiptLineItem> items
) {
}
