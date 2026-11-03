package com.bhive.bills.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public interface ScheduledBillConsentProvider {

    Optional<JsonNode> findValidConsent(ScheduledBillFetch scheduledBill);

    default boolean allowsRecurringAccess(
            ScheduledBillFetch scheduledBill,
            JsonNode consentArtifact
    ) {
        return false;
    }
}
