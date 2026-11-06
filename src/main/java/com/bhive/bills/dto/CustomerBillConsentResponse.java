package com.bhive.bills.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerBillConsentResponse(
        String consentRecordId,
        String providerCode,
        String providerName,
        List<String> requestedDataFields,
        String purposeCode,
        String statementVersion,
        String statementHash,
        String status,
        LocalDateTime acceptedAt,
        LocalDateTime revokedAt,
        Long savedBillId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
