package com.bhive.bills.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface ApiSetuConsentArtifactValidator {

    JsonNode requireVerifiedArtifact(
            Long customerId,
            String providerCode,
            JsonNode suppliedArtifact
    );
}
