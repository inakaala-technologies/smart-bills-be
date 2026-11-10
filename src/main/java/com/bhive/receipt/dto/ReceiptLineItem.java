package com.bhive.receipt.dto;

import java.math.BigDecimal;

public record ReceiptLineItem(
    String productName,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
}
