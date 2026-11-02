package com.bhive.bills.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

public record SavedBillResponse(
        Long id,
        String providerCode,
        String providerName,
        String categoryCode,
        String consumerNumber,
        Map<String, String> providerInputs,
        String responseFormat,
        String contentType,
        JsonNode jsonData,
        String xmlData,
        boolean pdfAvailable,
        LocalDateTime fetchedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
