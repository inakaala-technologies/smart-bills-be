package com.bhive.receipt.dto;

import java.time.Instant;

public record AdminPosPartnerApplicationResponse(
    String applicationId,
    String partnerName,
    String contactName,
    String contactEmail,
    String contactPhone,
    String retailerReference,
    String retailerName,
    String outletReference,
    String outletName,
    String publicKey,
    String status,
    String partnerId,
    String decisionReason,
    Instant submittedAt,
    Instant reviewedAt,
    Long reviewedBy
) {
}
