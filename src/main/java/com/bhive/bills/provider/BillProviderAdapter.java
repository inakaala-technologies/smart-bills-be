
package com.bhive.bills.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface BillProviderAdapter {

    String getProviderCode();

    default boolean supports(String providerCode) {
        return getProviderCode().equalsIgnoreCase(providerCode);
    }

    ProviderFetchResponse fetchBill(
            String providerCode,
            Map<String, String> providerInputs,
            JsonNode consentArtifact
    );
}
