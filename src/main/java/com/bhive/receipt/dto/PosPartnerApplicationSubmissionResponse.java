package com.bhive.receipt.dto;

public record PosPartnerApplicationSubmissionResponse(
    String applicationId,
    String statusAccessToken,
    String status
) {
}
