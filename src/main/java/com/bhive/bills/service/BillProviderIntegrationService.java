
package com.bhive.bills.service;

import com.bhive.bills.entity.ProviderInputField;
import com.bhive.bills.provider.BillProviderAdapter;
import com.bhive.bills.provider.BillProviderAdapterRegistry;
import com.bhive.bills.provider.BillProviderRequest;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import com.bhive.bills.entity.BillProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BillProviderIntegrationService {

    private static final Set<String> MANUALLY_FETCHABLE_CATEGORIES =
            Set.of("ELECTRICITY", "WATER", "ECHALLAN", "INSURANCE", "DOCUMENTS");
    private static final Set<String> SCHEDULED_CATEGORIES =
            Set.of("ELECTRICITY", "WATER", "ECHALLAN");

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BillProviderIntegrationService.class);

    private final BillProviderAdapterRegistry adapterRegistry;
    private final ProviderInputFieldRepository inputFieldRepository;
    private final BillProviderRepository providerRepository;
    private final SavedBillService savedBillService;
    private final CustomerBillConsentService customerBillConsentService;
    private final ApiSetuConsentArtifactValidator consentArtifactValidator;

    public BillProviderIntegrationService(
            BillProviderAdapterRegistry adapterRegistry,
            ProviderInputFieldRepository inputFieldRepository,
            BillProviderRepository providerRepository,
            SavedBillService savedBillService,
            CustomerBillConsentService customerBillConsentService,
            ApiSetuConsentArtifactValidator consentArtifactValidator) {

        this.adapterRegistry = adapterRegistry;
        this.inputFieldRepository = inputFieldRepository;
        this.providerRepository = providerRepository;
        this.savedBillService = savedBillService;
        this.customerBillConsentService = customerBillConsentService;
        this.consentArtifactValidator = consentArtifactValidator;
    }

    public ProviderFetchResponse fetchBill(BillProviderRequest request) {

        if (request == null || request.providerCode() == null
                || request.providerCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider code is required."
            );
        }

        String providerCode = request.providerCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        // Verify provider exists and is active
        BillProvider provider = providerRepository.findByCodeAndActiveTrue(providerCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Provider not found or inactive: " + providerCode
                ));

        // Validate user-provided fields
        validateProviderInputs(
                providerCode,
                request.providerInputs()
        );

        String categoryCode = provider.getCategory().getCode();
        if (!MANUALLY_FETCHABLE_CATEGORIES.contains(categoryCode)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "This bill category does not support manual fetching."
            );
        }

        Long customerId = savedBillService.requireAuthenticatedCustomerId();
        JsonNode verifiedConsentArtifact = requireVerifiedArtifact(
                customerId,
                providerCode,
                request.consentArtifact()
        );
        BillProviderAdapter adapter = adapterRegistry.getAdapter(providerCode);

        customerBillConsentService.authorizeAndPrepareManualFetch(
                request.applicationConsentRecordId(),
                customerId,
                providerCode,
                request.providerInputs().keySet().stream().sorted().toList(),
                request.providerInputs()
        );

        try {
            ProviderFetchResponse response = adapter.fetchBill(
                    providerCode,
                    request.providerInputs(),
                    verifiedConsentArtifact
            );
            savedBillService.saveFetchedBill(providerCode, request.providerInputs(), response);
            return response;
        } catch (RuntimeException failure) {
            try {
                savedBillService.recordFetchFailure(
                        providerCode,
                        request.providerInputs(),
                        fetchFailureCode(failure)
                );
            } catch (RuntimeException persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                LOGGER.error(
                        "Could not record failed bill fetch for provider {}.",
                        providerCode,
                        persistenceFailure
                );
            }
            throw failure;
        }
    }

    public ProviderFetchResponse fetchScheduledBill(
            ScheduledBillFetch claim,
            String providerCode,
            Map<String, String> providerInputs,
            JsonNode consentArtifact
    ) {
        BillProvider provider = providerRepository.findByCodeAndActiveTrue(providerCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Provider not found or inactive."
                ));
        String categoryCode = provider.getCategory().getCode();
        if (!SCHEDULED_CATEGORIES.contains(categoryCode)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "This bill category cannot be scheduled."
            );
        }
        validateProviderInputs(providerCode, providerInputs);
        JsonNode verifiedConsentArtifact = requireVerifiedArtifact(
                claim.customerId(),
                providerCode,
                consentArtifact
        );
        return adapterRegistry.getAdapter(providerCode)
                .fetchBill(providerCode, providerInputs, verifiedConsentArtifact);
    }

    private JsonNode requireVerifiedArtifact(
            Long customerId,
            String providerCode,
            JsonNode suppliedArtifact
    ) {
        if (suppliedArtifact == null || suppliedArtifact.isNull()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A verified API Setu consent artifact is not available."
            );
        }
        JsonNode verifiedArtifact = consentArtifactValidator.requireVerifiedArtifact(
                customerId,
                providerCode,
                suppliedArtifact
        );
        if (verifiedArtifact == null || verifiedArtifact.isNull()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A verified API Setu consent artifact is not available."
            );
        }
        return verifiedArtifact;
    }

    private String fetchFailureCode(RuntimeException failure) {
        if (failure instanceof ResourceAccessException) {
            return "PROVIDER_CONNECTION_ERROR";
        }
        if (failure instanceof RestClientResponseException) {
            return "PROVIDER_HTTP_ERROR";
        }
        return "PROVIDER_FETCH_ERROR";
    }

    private void validateProviderInputs(
            String providerCode,
            Map<String, String> providerInputs) {

        if (providerInputs == null) {
            throw new IllegalArgumentException(
                    "Provider inputs are required."
            );
        }

        List<ProviderInputField> fields =
                inputFieldRepository
                        .findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc(
                                providerCode
                        );

        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active input fields configured for provider: "
                            + providerCode
            );
        }

        Set<String> allowedKeys = new HashSet<>();

        // Validate required fields
        for (ProviderInputField field : fields) {

            String fieldKey = field.getFieldKey();
            allowedKeys.add(fieldKey);

            String value = providerInputs.get(fieldKey);

            if (field.isRequired()
                    && (value == null || value.isBlank())) {

                throw new IllegalArgumentException(
                        field.getLabel() + " is required."
                );
            }
        }

        // Reject unsupported input keys
        for (String suppliedKey : providerInputs.keySet()) {

            if (!allowedKeys.contains(suppliedKey)) {
                throw new IllegalArgumentException(
                        "Invalid input field for provider: " + suppliedKey
                );
            }
        }
    }
}
