package com.bhive.bills.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UnavailableApiSetuConsentArtifactValidator
        implements ApiSetuConsentArtifactValidator {

    @Override
    public JsonNode requireVerifiedArtifact(
            Long customerId,
            String providerCode,
            JsonNode suppliedArtifact
    ) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "A verified API Setu consent artifact is not available."
        );
    }
}
