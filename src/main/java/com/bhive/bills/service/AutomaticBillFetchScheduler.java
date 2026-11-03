package com.bhive.bills.service;

import com.bhive.bills.config.BillFetchSchedulerProperties;
import com.bhive.bills.provider.ProviderApiException;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Component
public class AutomaticBillFetchScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AutomaticBillFetchScheduler.class);
    private static final TypeReference<Map<String, String>> INPUT_MAP_TYPE =
            new TypeReference<>() {
            };

    private final SavedBillPersistenceService persistenceService;
    private final BillProviderIntegrationService integrationService;
    private final SavedBillService savedBillService;
    private final ScheduledBillConsentProvider consentProvider;
    private final BillFetchSchedulerProperties properties;
    private final ObjectMapper objectMapper;

    public AutomaticBillFetchScheduler(
            SavedBillPersistenceService persistenceService,
            BillProviderIntegrationService integrationService,
            SavedBillService savedBillService,
            ScheduledBillConsentProvider consentProvider,
            BillFetchSchedulerProperties properties,
            ObjectMapper objectMapper
    ) {
        this.persistenceService = persistenceService;
        this.integrationService = integrationService;
        this.savedBillService = savedBillService;
        this.consentProvider = consentProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.bills.fetch.scheduler-delay-ms:60000}")
    public void fetchDueBills() {
        for (ScheduledBillFetch claim : persistenceService.claimEligibleFetches(
                LocalDateTime.now(),
                properties.getBatchSize(),
                properties.getStaleClaimMinutes()
        )) {
            process(claim);
        }
    }

    void process(ScheduledBillFetch claim) {
        try {
            Optional<JsonNode> consent = consentProvider.findValidConsent(claim);
            if (consent.isEmpty() || consent.get().isNull()) {
                savedBillService.deferScheduledFetch(claim, properties.getIntervalHours());
                return;
            }
            if ("ECHALLAN".equals(claim.categoryCode())
                    && !consentProvider.allowsRecurringAccess(claim, consent.get())) {
                savedBillService.deferScheduledFetch(claim, properties.getIntervalHours());
                return;
            }

            Map<String, String> inputs = objectMapper.readValue(
                    claim.providerInputsJson(),
                    INPUT_MAP_TYPE
            );
            ProviderFetchResponse response = integrationService.fetchScheduledBill(
                    claim.providerCode(),
                    inputs,
                    consent.get()
            );
            savedBillService.completeScheduledFetch(claim, response);
        } catch (Exception exception) {
            LOGGER.warn(
                    "Scheduled bill fetch failed for saved bill {} and provider {} ({}).",
                    claim.savedBillId(),
                    claim.providerCode(),
                    exception.getClass().getSimpleName()
            );
            fail(claim, failureCode(exception), isTemporary(exception));
        }
    }

    private void fail(ScheduledBillFetch claim, String errorCode, boolean temporary) {
        try {
            savedBillService.failScheduledFetch(
                    claim,
                    errorCode,
                    temporary,
                    properties.getRetryBaseDelaySeconds(),
                    properties.getRetryMaxDelaySeconds(),
                    properties.getMaxRetries()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not record scheduled bill fetch failure for saved bill {}.",
                    claim.savedBillId(),
                    exception
            );
        }
    }

    private boolean isTemporary(Exception exception) {
        if (exception instanceof ProviderApiException providerException) {
            return providerException.isTemporary();
        }
        return exception instanceof ResourceAccessException
                || exception instanceof RestClientResponseException;
    }

    private String failureCode(Exception exception) {
        if (exception instanceof ProviderApiException providerException) {
            return providerException.getStatusCode() == 429
                    ? "PROVIDER_RATE_LIMIT"
                    : providerException.isTemporary()
                    ? "PROVIDER_SERVER_ERROR"
                    : "PROVIDER_HTTP_ERROR";
        }
        if (exception instanceof ResourceAccessException) {
            return "PROVIDER_CONNECTION_ERROR";
        }
        if (exception instanceof RestClientResponseException) {
            return "PROVIDER_HTTP_ERROR";
        }
        return "SCHEDULED_FETCH_ERROR";
    }
}
