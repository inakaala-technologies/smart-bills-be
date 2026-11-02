
package com.bhive.bills.service;

import com.bhive.bills.entity.ProviderInputField;
import com.bhive.bills.provider.BillProviderAdapter;
import com.bhive.bills.provider.BillProviderAdapterRegistry;
import com.bhive.bills.provider.BillProviderRequest;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import com.bhive.bills.entity.BillProvider;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BillProviderIntegrationService {

    private final BillProviderAdapterRegistry adapterRegistry;
    private final ProviderInputFieldRepository inputFieldRepository;
    private final BillProviderRepository providerRepository;
    private final SavedBillService savedBillService;

    public BillProviderIntegrationService(
            BillProviderAdapterRegistry adapterRegistry,
            ProviderInputFieldRepository inputFieldRepository,
            BillProviderRepository providerRepository,
            SavedBillService savedBillService) {

        this.adapterRegistry = adapterRegistry;
        this.inputFieldRepository = inputFieldRepository;
        this.providerRepository = providerRepository;
        this.savedBillService = savedBillService;
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

        // Find the appropriate provider adapter
        BillProviderAdapter adapter =
                adapterRegistry.getAdapter(providerCode);

        // Validate user-provided fields
        validateProviderInputs(
                providerCode,
                request.providerInputs()
        );

        String categoryCode = provider.getCategory().getCode();
        if (!"ELECTRICITY".equals(categoryCode) && !"WATER".equals(categoryCode)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only electricity and water bills can be fetched and saved."
            );
        }

        savedBillService.requireAuthenticatedCustomerId();

        // Fetch bill in JSON, XML, or PDF format
        ProviderFetchResponse response = adapter.fetchBill(
                providerCode,
                request.providerInputs(),
                request.consentArtifact()
        );
        savedBillService.saveFetchedBill(providerCode, request.providerInputs(), response);
        return response;
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
