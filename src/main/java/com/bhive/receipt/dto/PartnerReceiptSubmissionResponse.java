package com.bhive.receipt.dto;

import java.time.Instant;

public record PartnerReceiptSubmissionResponse(
    Long receiptId,
    String externalReceiptId,
    String receiptNumber,
    String status,
    Instant receivedAt
) {
}
