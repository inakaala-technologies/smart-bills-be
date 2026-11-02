
package com.bhive.bills.provider;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillProviderAdapterRegistry {

    private final List<BillProviderAdapter> adapters;

    public BillProviderAdapterRegistry(
            List<BillProviderAdapter> adapterList) {
        this.adapters = adapterList;
    }

    public BillProviderAdapter getAdapter(String providerCode) {

        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Provider code cannot be empty"
            );
        }

        // First, look for a provider-specific adapter.
        return adapters.stream()
                .filter(adapter ->
                        !adapter.getProviderCode().equalsIgnoreCase(
                                "CONFIGURATION_DRIVEN"
                        ))
                .filter(adapter ->
                        adapter.supports(providerCode))
                .findFirst()

                // If none exists, use the configuration-driven adapter.
                .orElseGet(() ->
                        adapters.stream()
                                .filter(adapter ->
                                        adapter.getProviderCode()
                                                .equalsIgnoreCase(
                                                        "CONFIGURATION_DRIVEN"
                                                ))
                                .filter(adapter ->
                                        adapter.supports(providerCode))
                                .findFirst()
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "No bill provider adapter found for: "
                                                        + providerCode
                                        ))
                );
    }
}
