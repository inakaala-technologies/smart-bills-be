package com.bhive.bills.provider;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record BillProviderRequest(

        @NotBlank
        String providerCode,

        @NotEmpty
        Map<String, String> providerInputs,

        JsonNode consentArtifact

) {
}