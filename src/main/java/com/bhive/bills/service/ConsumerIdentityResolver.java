package com.bhive.bills.service;

import com.bhive.bills.config.ApiSetuServiceCatalog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ConsumerIdentityResolver {

    private static final Map<String, List<String>> IDENTITY_FIELDS = Map.ofEntries(
            Map.entry("KSEB", List.of("ConsumerNo")),
            Map.entry("GUVNL", List.of("consno")),
            Map.entry("PSPCL", List.of("PSPCLACCOUNT")),
            Map.entry("JKPDD", List.of("consumerid")),
            Map.entry("NBPDCL", List.of("ConsumerId")),
            Map.entry("BSESDELHI", List.of("ca_number")),
            Map.entry("MPWZ", List.of("consumer_no")),
            Map.entry("UPPCL", List.of("AccountNo", "Discom")),
            Map.entry("GOAONLINE", List.of("UDF1", "UID")),
            Map.entry("KIADB", List.of("RegNum")),
            Map.entry("ULB_UP", List.of("CONNECTIONNO"))
    );

    public ConsumerIdentity resolve(String providerCode, Map<String, String> providerInputs) {
        String normalizedProviderCode = providerCode == null
                ? ""
                : providerCode.trim().toUpperCase(Locale.ROOT);
        List<String> fields = IDENTITY_FIELDS.get(normalizedProviderCode);
        if (fields == null) {
            fields = ApiSetuServiceCatalog.find(normalizedProviderCode)
                    .map(ApiSetuServiceCatalog.ServiceDefinition::inputFields)
                    .orElse(null);
        }
        if (fields == null) {
            throw new IllegalArgumentException(
                    "No consumer identity mapping is configured for provider: "
                            + normalizedProviderCode
            );
        }
        if (providerInputs == null) {
            throw new IllegalArgumentException("Provider inputs are required.");
        }

        StringBuilder canonicalIdentity = new StringBuilder(normalizedProviderCode).append('|');
        String primaryConsumerNumber = null;
        for (String field : fields) {
            String value = providerInputs.get(field);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Consumer identity field is required: " + field
                );
            }
            String normalizedValue = value.trim();
            if (primaryConsumerNumber == null) {
                primaryConsumerNumber = normalizedValue;
            }
            canonicalIdentity
                    .append(field.length()).append(':').append(field)
                    .append('=').append(normalizedValue.length()).append(':')
                    .append(normalizedValue).append(';');
        }

        return new ConsumerIdentity(
                primaryConsumerNumber,
                sha256(canonicalIdentity.toString()),
                fields
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
