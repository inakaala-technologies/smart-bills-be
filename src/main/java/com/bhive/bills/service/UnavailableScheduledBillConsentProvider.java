package com.bhive.bills.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public class UnavailableScheduledBillConsentProvider implements ScheduledBillConsentProvider {

    @Override
    public Optional<JsonNode> findValidConsent(ScheduledBillFetch scheduledBill) {
        return Optional.empty();
    }
}
