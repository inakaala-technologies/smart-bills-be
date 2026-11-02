
package com.bhive.bills.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProviderRequestTemplateProcessor {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([^{}]+)}}");

    public Map<String, Object> process(
            Map<String, Object> template,
            Map<String, String> providerInputs,
            JsonNode consentArtifact) {

        // Validate request template
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException(
                    "Provider request template cannot be empty."
            );
        }

        // Validate provider inputs
        if (providerInputs == null) {
            throw new IllegalArgumentException(
                    "Provider inputs cannot be null."
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // Generate one transaction ID for this request
        String transactionId = UUID.randomUUID().toString();

        for (Map.Entry<String, Object> entry : template.entrySet()) {

            result.put(
                    entry.getKey(),
                    resolveValue(
                            entry.getValue(),
                            providerInputs,
                            consentArtifact,
                            transactionId
                    )
            );
        }

        return result;
    }

    private Object resolveValue(
            Object value,
            Map<String, String> providerInputs,
            JsonNode consentArtifact,
            String transactionId) {

        // Resolve nested JSON objects
        if (value instanceof Map<?, ?> map) {

            Map<String, Object> resolved = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {

                resolved.put(
                        String.valueOf(entry.getKey()),
                        resolveValue(
                                entry.getValue(),
                                providerInputs,
                                consentArtifact,
                                transactionId
                        )
                );
            }

            return resolved;
        }

        // Resolve arrays
        if (value instanceof List<?> list) {

            List<Object> resolved = new ArrayList<>();

            for (Object item : list) {

                resolved.add(
                        resolveValue(
                                item,
                                providerInputs,
                                consentArtifact,
                                transactionId
                        )
                );
            }

            return resolved;
        }

        // Preserve non-string values
        if (!(value instanceof String text)) {
            return value;
        }

        Matcher matcher = PLACEHOLDER.matcher(text);

        // Handle a placeholder occupying the entire value
        if (matcher.matches()) {

            return resolvePlaceholder(
                    matcher.group(1),
                    providerInputs,
                    consentArtifact,
                    transactionId
            );
        }

        // Handle placeholders embedded in strings
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {

            Object replacement = resolvePlaceholder(
                    matcher.group(1),
                    providerInputs,
                    consentArtifact,
                    transactionId
            );

            if (!(replacement instanceof String)) {

                throw new IllegalArgumentException(
                        "Object placeholders must occupy the entire template value."
                );
            }

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement((String) replacement)
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private Object resolvePlaceholder(
            String placeholder,
            Map<String, String> providerInputs,
            JsonNode consentArtifact,
            String transactionId) {

        // Transaction ID
        if (placeholder.equals("txnId")) {
            return transactionId;
        }

        // Consent artifact
        if (placeholder.equals("consentArtifact")) {

            if (consentArtifact == null || consentArtifact.isNull()) {

                throw new IllegalArgumentException(
                        "Consent artifact is required by the provider template."
                );
            }

            return consentArtifact.deepCopy();
        }

        // Provider input values
        if (placeholder.startsWith("input.")) {

            String fieldKey =
                    placeholder.substring("input.".length());

            String inputValue = providerInputs.get(fieldKey);

            if (inputValue == null || inputValue.isBlank()) {

                throw new IllegalArgumentException(
                        "Missing or empty provider input: " + fieldKey
                );
            }

            return inputValue;
        }

        throw new IllegalArgumentException(
                "Unsupported request template placeholder: " + placeholder
        );
    }
}
