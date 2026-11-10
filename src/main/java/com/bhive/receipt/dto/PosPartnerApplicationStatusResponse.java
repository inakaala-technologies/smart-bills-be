package com.bhive.receipt.dto;

import java.time.Instant;

public record PosPartnerApplicationStatusResponse(
    String applicationId,
    String status,
    String partnerId,
    String decisionReason,
    Instant submittedAt,
    Instant reviewedAt
) {
}
