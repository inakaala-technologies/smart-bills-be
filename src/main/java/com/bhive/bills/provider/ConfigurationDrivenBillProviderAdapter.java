
package com.bhive.bills.provider;

import com.bhive.bills.config.ApiSetuProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ConfigurationDrivenBillProviderAdapter
        implements BillProviderAdapter {

    private static final String ADAPTER_CODE = "CONFIGURATION_DRIVEN";

    private final RestClient restClient;
    private final ApiSetuProperties properties;
    private final ProviderRequestTemplateProcessor templateProcessor;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public ConfigurationDrivenBillProviderAdapter(
            RestClient apiSetuRestClient,
            ApiSetuProperties properties,
            ProviderRequestTemplateProcessor templateProcessor,
            ObjectMapper objectMapper) {

        this.restClient = apiSetuRestClient;
        this.properties = properties;
        this.templateProcessor = templateProcessor;
        this.objectMapper = objectMapper;
        this.xmlMapper = new XmlMapper();
    }

    @Override
    public String getProviderCode() {
        return ADAPTER_CODE;
    }

    @Override
    public boolean supports(String providerCode) {

        if (providerCode == null || providerCode.isBlank()
                || properties.getProviders() == null) {
            return false;
        }

        return properties.getProviders().keySet().stream()
                .anyMatch(code -> code.equalsIgnoreCase(providerCode));
    }

    @Override
    public ProviderFetchResponse fetchBill(
            String providerCode,
            Map<String, String> providerInputs,
            JsonNode consentArtifact) {

        ApiSetuProperties.ProviderConfig config =
                properties.getProviders().entrySet().stream()
                        .filter(entry -> entry.getKey()
                                .equalsIgnoreCase(providerCode))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No API configuration found for provider: "
                                                + providerCode
                                ));

        if (config.getEndpoint() == null
                || config.getEndpoint().isBlank()) {
            throw new IllegalStateException(
                    "API endpoint is not configured for provider: "
                            + providerCode
            );
        }

        String requestFormat = normalizeFormat(
                config.getRequestFormat(), "JSON"
        );

        String responseFormat = normalizeFormat(
                config.getResponseFormat(), "JSON"
        );

        validateFormats(requestFormat, responseFormat);

        Map<String, Object> requestBody =
                templateProcessor.process(
                        config.getRequestTemplate(),
                        providerInputs,
                        consentArtifact
                );

        if (requestBody == null || requestBody.isEmpty()) {
            throw new IllegalStateException(
                    "Request template is missing for provider: "
                            + providerCode
            );
        }

        HttpMethod method = resolveHttpMethod(config.getHttpMethod());

        String serializedRequest = serializeRequest(
                requestBody, requestFormat
        );

        RestClient.RequestBodySpec request = restClient
                .method(method)
                .uri(config.getEndpoint())
                .headers(headers ->
                        applyHeaders(
                                headers,
                                config,
                                requestFormat,
                                responseFormat
                        )
                );

        if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
            throw new IllegalArgumentException(
                    "GET and HEAD requests with configured request templates "
                            + "are not supported yet for provider: "
                            + providerCode
            );
        }

        return request
                .body(serializedRequest)
                .exchange((clientRequest, clientResponse) -> {

                    if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException(
                                "Provider API returned HTTP status: "
                                        + clientResponse.getStatusCode()
                                        + " for provider: " + providerCode
                        );
                    }

                    String actualContentType =
                            clientResponse.getHeaders().getContentType() != null
                                    ? clientResponse.getHeaders()
                                            .getContentType().toString()
                                    : null;

                    return readResponse(
                            responseFormat,
                            actualContentType,
                            clientResponse.getBody().readAllBytes()
                    );
                });
    }

    private ProviderFetchResponse readResponse(
            String responseFormat,
            String contentType,
            byte[] responseBytes) {

        try {
            return switch (responseFormat) {

                case "JSON" -> new ProviderFetchResponse(
                        "JSON",
                        contentType,
                        objectMapper.readTree(responseBytes),
                        null,
                        null
                );

                case "XML" -> new ProviderFetchResponse(
                        "XML",
                        contentType,
                        null,
                        new String(responseBytes, StandardCharsets.UTF_8),
                        null
                );

                case "PDF" -> new ProviderFetchResponse(
                        "PDF",
                        contentType,
                        null,
                        null,
                        responseBytes
                );

                default -> throw new IllegalArgumentException(
                        "Unsupported response format: " + responseFormat
                );
            };

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to process provider response as "
                            + responseFormat,
                    exception
            );
        }
    }

    private String serializeRequest(
            Map<String, Object> requestBody,
            String requestFormat) {

        try {
            return switch (requestFormat) {
                case "JSON" -> objectMapper.writeValueAsString(requestBody);
                case "XML" -> xmlMapper.writeValueAsString(requestBody);
                default -> throw new IllegalArgumentException(
                        "Unsupported request format: " + requestFormat
                );
            };

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize provider request as "
                            + requestFormat,
                    exception
            );
        }
    }

    private void applyHeaders(
            HttpHeaders headers,
            ApiSetuProperties.ProviderConfig config,
            String requestFormat,
            String responseFormat) {

        String defaultContentType =
                requestFormat.equals("XML")
                        ? "application/xml"
                        : "application/json";

        String defaultAccept = switch (responseFormat) {
            case "XML" -> "application/xml";
            case "PDF" -> "application/pdf";
            default -> "application/json";
        };

        headers.set(
                HttpHeaders.CONTENT_TYPE,
                config.getContentType() != null
                        && !config.getContentType().isBlank()
                        ? config.getContentType()
                        : defaultContentType
        );

        headers.set(
                HttpHeaders.ACCEPT,
                config.getAccept() != null
                        && !config.getAccept().isBlank()
                        ? config.getAccept()
                        : defaultAccept
        );

        if (config.getHeaders() != null) {
            config.getHeaders().forEach(headers::set);
        }

        String apiKey = firstNonBlank(
                config.getApiKey(),
                properties.getApiKey()
        );

        String clientId = firstNonBlank(
                config.getClientId(),
                properties.getClientId()
        );

        if (apiKey != null) {
            headers.set("X-APISETU-APIKEY", apiKey);
        }

        if (clientId != null) {
            headers.set("X-APISETU-CLIENTID", clientId);
        }
    }

    private String normalizeFormat(String format, String defaultFormat) {
        if (format == null || format.isBlank()) {
            return defaultFormat;
        }

        return format.trim().toUpperCase(Locale.ROOT);
    }

    private void validateFormats(
            String requestFormat,
            String responseFormat) {

        if (!List.of("JSON", "XML").contains(requestFormat)) {
            throw new IllegalArgumentException(
                    "Unsupported request format: " + requestFormat
            );
        }

        if (!List.of("JSON", "XML", "PDF").contains(responseFormat)) {
            throw new IllegalArgumentException(
                    "Unsupported response format: " + responseFormat
            );
        }
    }

    private HttpMethod resolveHttpMethod(String configuredMethod) {
        return HttpMethod.valueOf(
                configuredMethod == null || configuredMethod.isBlank()
                        ? "POST"
                        : configuredMethod.trim().toUpperCase(Locale.ROOT)
        );
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return null;
    }
}
