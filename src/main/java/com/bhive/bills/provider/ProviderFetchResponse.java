
package com.bhive.bills.provider;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderFetchResponse(
        String format,
        String contentType,
        JsonNode jsonData,
        String xmlData,
        byte[] pdfData
) {
}
