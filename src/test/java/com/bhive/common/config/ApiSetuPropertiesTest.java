package com.bhive.common.config;

import com.bhive.bills.config.ApiSetuProperties;
import com.bhive.bills.provider.ConfigurationDrivenBillProviderAdapter;
import com.bhive.bills.provider.ProviderRequestTemplateProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSetuPropertiesTest {

    private static final List<String> PROVIDER_CODES = List.of(
            "KSEB",
            "GUVNL",
            "PSPCL",
            "JKPDD",
            "NBPDCL",
            "BSESDELHI",
            "MPWZ",
            "UPPCL",
            "GOAONLINE",
            "KIADB",
            "ULB_UP"
    );

    private static final Map<String, List<String>> EXPECTED_FIELDS = new java.util.LinkedHashMap<>();

    static {
        EXPECTED_FIELDS.put("KSEB", List.of("ConsumerNo"));
        EXPECTED_FIELDS.put("GUVNL", List.of("discom", "consno"));
        EXPECTED_FIELDS.put("PSPCL", List.of("PSPCLACCOUNT", "MOBILE"));
        EXPECTED_FIELDS.put("JKPDD", List.of("consumerid", "installationNo", "month"));
        EXPECTED_FIELDS.put("NBPDCL", List.of("ConsumerId"));
        EXPECTED_FIELDS.put("BSESDELHI", List.of("ca_number"));
        EXPECTED_FIELDS.put("MPWZ", List.of("consumer_no"));
        EXPECTED_FIELDS.put("UPPCL", List.of("AccountNo", "Discom"));
        EXPECTED_FIELDS.put("GOAONLINE", List.of("UDF1", "UDF2", "UID"));
        EXPECTED_FIELDS.put("KIADB", List.of("RegNum"));
        EXPECTED_FIELDS.put("ULB_UP", List.of("CONNECTIONNO"));
    }

    @Test
    void binds_all_configured_provider_definitions() {
        MockEnvironment environment = createEnvironment();
        ApiSetuProperties properties = Binder.get(environment)
                .bind("app.apisetu", Bindable.of(ApiSetuProperties.class))
                .orElse(null);
        assertNotNull(properties, "Binding failed for app.apisetu");

        assertEquals("https://sandbox.api-setu.in", properties.getBaseUrl());
        assertEquals("test-api-key", properties.getApiKey());
        assertEquals("test-client-id", properties.getClientId());
        assertEquals(PROVIDER_CODES.size(), properties.getProviders().size());

        for (String providerCode : PROVIDER_CODES) {
            ApiSetuProperties.ProviderConfig config = properties.getProviders().get(providerCode);
            assertNotNull(config, "Missing config for provider: " + providerCode);
            assertNotNull(config.getEndpoint(), "Missing endpoint for provider: " + providerCode);
            assertNotNull(config.getRequestTemplate(), "Missing request template for provider: " + providerCode);
            assertEquals("POST", config.getHttpMethod());
            assertEquals("application/json", config.getContentType());
        }
    }

    @Test
    void resolves_request_templates_for_each_provider() throws Exception {
        MockEnvironment environment = createEnvironment();
        ApiSetuProperties properties = Binder.get(environment)
                .bind("app.apisetu", Bindable.of(ApiSetuProperties.class))
                .orElse(null);
        assertNotNull(properties, "Binding failed for app.apisetu");

        ProviderRequestTemplateProcessor processor = new ProviderRequestTemplateProcessor();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode consentArtifact = objectMapper.readTree("{\"consent\":{\"consentId\":\"consent-123\"},\"signature\":{\"signature\":\"sig-123\"}}");

        Map<String, Map<String, String>> providerInputs = new java.util.LinkedHashMap<>();
        providerInputs.put("KSEB", Map.of("ConsumerNo", "1111111111111"));
        providerInputs.put("GUVNL", Map.of("discom", "GUVNL", "consno", "1234567890"));
        providerInputs.put("PSPCL", Map.of("PSPCLACCOUNT", "ABCD123", "MOBILE", "9999999999"));
        providerInputs.put("JKPDD", Map.of("consumerid", "C123", "installationNo", "I456", "month", "2026-09"));
        providerInputs.put("NBPDCL", Map.of("ConsumerId", "NB123"));
        providerInputs.put("BSESDELHI", Map.of("ca_number", "CA123"));
        providerInputs.put("MPWZ", Map.of("consumer_no", "MP123"));
        providerInputs.put("UPPCL", Map.of("AccountNo", "ACC123", "Discom", "UPPCL"));
        providerInputs.put("GOAONLINE", Map.of("UDF1", "U1", "UDF2", "U2", "UID", "UID123"));
        providerInputs.put("KIADB", Map.of("RegNum", "R123"));
        providerInputs.put("ULB_UP", Map.of("CONNECTIONNO", "CONN123"));

        for (String providerCode : PROVIDER_CODES) {
            Map<String, Object> template = properties.getProviders().get(providerCode).getRequestTemplate();
            Map<String, Object> request = processor.process(template, providerInputs.get(providerCode), consentArtifact);

            assertNotNull(request, "Request generated for provider: " + providerCode);
            assertTrue(request.containsKey("txnId"), "txnId missing for provider: " + providerCode);
            assertEquals("xml", request.get("format"), "format mismatch for provider: " + providerCode);
            assertTrue(request.containsKey("consentArtifact"), "Consent artifact missing for provider: " + providerCode);
            assertTrue(request.containsKey("certificateParameters"), "certificateParameters missing for provider: " + providerCode);
        }
    }

    @Test
    void verifies_provider_input_fields_match_expected_metadata() {
        MockEnvironment environment = createEnvironment();
        ApiSetuProperties properties = Binder.get(environment)
                .bind("app.apisetu", Bindable.of(ApiSetuProperties.class))
                .orElse(null);
        assertNotNull(properties, "Binding failed for app.apisetu");

        for (String providerCode : PROVIDER_CODES) {
            Map<String, Object> template = properties.getProviders().get(providerCode).getRequestTemplate();
            assertNotNull(template, "Missing template for provider: " + providerCode);

            Map<String, Object> certificateParameters =
                    (Map<String, Object>) template.get("certificateParameters");
            assertNotNull(certificateParameters, "Missing certificateParameters for provider: " + providerCode);

            List<String> expectedFields = EXPECTED_FIELDS.get(providerCode);
            assertNotNull(expectedFields, "No expected field list for provider: " + providerCode);

            for (String fieldName : expectedFields) {
                assertTrue(
                        certificateParameters.containsKey(fieldName),
                        "Missing template field " + fieldName + " for provider " + providerCode
                );
            }
        }
    }

    @Test
    void constructs_requests_for_all_providers_with_mock_http_responses() throws Exception {
        MockEnvironment environment = createEnvironment();
        ApiSetuProperties properties = Binder.get(environment)
                .bind("app.apisetu", Bindable.of(ApiSetuProperties.class))
                .orElse(null);
        assertNotNull(properties, "Binding failed for app.apisetu");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode consentArtifact = objectMapper.readTree("{\"consent\":{\"consentId\":\"consent-123\"},\"signature\":{\"signature\":\"sig-123\"}}");

        Map<String, Map<String, String>> providerInputs = new java.util.LinkedHashMap<>();
        providerInputs.put("KSEB", Map.of("ConsumerNo", "1111111111111"));
        providerInputs.put("GUVNL", Map.of("discom", "GUVNL", "consno", "1234567890"));
        providerInputs.put("PSPCL", Map.of("PSPCLACCOUNT", "ABCD123", "MOBILE", "9999999999"));
        providerInputs.put("JKPDD", Map.of("consumerid", "C123", "installationNo", "I456", "month", "2026-09"));
        providerInputs.put("NBPDCL", Map.of("ConsumerId", "NB123"));
        providerInputs.put("BSESDELHI", Map.of("ca_number", "CA123"));
        providerInputs.put("MPWZ", Map.of("consumer_no", "MP123"));
        providerInputs.put("UPPCL", Map.of("AccountNo", "ACC123", "Discom", "UPPCL"));
        providerInputs.put("GOAONLINE", Map.of("UDF1", "U1", "UDF2", "U2", "UID", "UID123"));
        providerInputs.put("KIADB", Map.of("RegNum", "R123"));
        providerInputs.put("ULB_UP", Map.of("CONNECTIONNO", "CONN123"));

        for (String providerCode : PROVIDER_CODES) {
            ApiSetuProperties.ProviderConfig config = properties.getProviders().get(providerCode);
            assertNotNull(config, "Missing config for provider: " + providerCode);

            RestClient.Builder restClientBuilder = RestClient.builder()
                    .baseUrl("https://sandbox.api-setu.in");
            MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
            RestClient restClient = restClientBuilder.build();
            var expectation = server.expect(requestTo("https://sandbox.api-setu.in" + config.getEndpoint()))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", "application/json"))
                    .andExpect(header("Accept", "application/json"))
                    .andExpect(header("X-APISETU-APIKEY", "test-api-key"))
                    .andExpect(header("X-APISETU-CLIENTID", "test-client-id"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.txnId").value(matchesPattern(
                            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    )))
                    .andExpect(jsonPath("$.format").value("xml"))
                    .andExpect(jsonPath("$.certificateParameters").isMap())
                    .andExpect(jsonPath("$.consentArtifact.consent.consentId").value("consent-123"))
                    .andExpect(jsonPath("$.consentArtifact.signature.signature").value("sig-123"));
            for (String fieldName : EXPECTED_FIELDS.get(providerCode)) {
                expectation.andExpect(jsonPath("$.certificateParameters." + fieldName)
                        .value(providerInputs.get(providerCode).get(fieldName)));
            }
            expectation.andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));

            ConfigurationDrivenBillProviderAdapter adapter = new ConfigurationDrivenBillProviderAdapter(
                    restClient,
                    properties,
                    new ProviderRequestTemplateProcessor(),
                    objectMapper
            );
            var response = adapter.fetchBill(providerCode, providerInputs.get(providerCode), consentArtifact);

            assertNotNull(response, "Mock response missing for provider: " + providerCode);
            assertEquals("OK", response.jsonData().path("status").asText());
            server.verify();
        }
    }

    private MockEnvironment createEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.apisetu.base-url", "https://sandbox.api-setu.in");
        environment.setProperty("app.apisetu.api-key", "test-api-key");
        environment.setProperty("app.apisetu.client-id", "test-client-id");

        addProvider(environment, "KSEB", "/certificate/v3/kseb/elebl", Map.of("ConsumerNo", "{{input.ConsumerNo}}"));
        addProvider(environment, "GUVNL", "/certificate/v3/guvnl/elebl", Map.of("discom", "{{input.discom}}", "consno", "{{input.consno}}"));
        addProvider(environment, "PSPCL", "/certificate/v3/pspcl/elebl", Map.of("PSPCLACCOUNT", "{{input.PSPCLACCOUNT}}", "MOBILE", "{{input.MOBILE}}"));
        addProvider(environment, "JKPDD", "/certificate/v3/kpdcl/elebl", Map.of("consumerid", "{{input.consumerid}}", "installationNo", "{{input.installationNo}}", "month", "{{input.month}}"));
        addProvider(environment, "NBPDCL", "/certificate/v3/nbpdcl/elebl", Map.of("ConsumerId", "{{input.ConsumerId}}"));
        addProvider(environment, "BSESDELHI", "/certificate/v3/bsesdelhi/elebl", Map.of("ca_number", "{{input.ca_number}}"));
        addProvider(environment, "MPWZ", "/certificate/v3/mpwz/elebl", Map.of("consumer_no", "{{input.consumer_no}}"));
        addProvider(environment, "UPPCL", "/certificate/v3/upenergy/elebl", Map.of("AccountNo", "{{input.AccountNo}}", "Discom", "{{input.Discom}}"));
        addProvider(environment, "GOAONLINE", "/certificate/v3/goaonline/wtrbl", Map.of("UDF1", "{{input.UDF1}}", "UDF2", "{{input.UDF2}}", "UID", "{{input.UID}}"));
        addProvider(environment, "KIADB", "/certificate/v3/kiadb/wtrbl", Map.of("RegNum", "{{input.RegNum}}"));
        addProvider(environment, "ULB_UP", "/certificate/v3/nagarsewaup/wtrbl", Map.of("CONNECTIONNO", "{{input.CONNECTIONNO}}"));

        return environment;
    }

    private void addProvider(MockEnvironment environment, String providerCode, String endpoint, Map<String, String> certificateParameters) {
        environment.setProperty("app.apisetu.providers." + providerCode + ".endpoint", endpoint);
        environment.setProperty("app.apisetu.providers." + providerCode + ".http-method", "POST");
        environment.setProperty("app.apisetu.providers." + providerCode + ".request-format", "JSON");
        environment.setProperty("app.apisetu.providers." + providerCode + ".response-format", "JSON");
        environment.setProperty("app.apisetu.providers." + providerCode + ".content-type", "application/json");
        environment.setProperty("app.apisetu.providers." + providerCode + ".accept", "application/json");
        environment.setProperty("app.apisetu.providers." + providerCode + ".headers.Accept", "application/json");
        environment.setProperty("app.apisetu.providers." + providerCode + ".headers.Content-Type", "application/json");
        environment.setProperty("app.apisetu.providers." + providerCode + ".request-template.txnId", "{{txnId}}");
        environment.setProperty("app.apisetu.providers." + providerCode + ".request-template.format", "xml");

        for (Map.Entry<String, String> entry : certificateParameters.entrySet()) {
            environment.setProperty(
                    "app.apisetu.providers." + providerCode + ".request-template.certificateParameters." + entry.getKey(),
                    entry.getValue()
            );
        }

        environment.setProperty("app.apisetu.providers." + providerCode + ".request-template.consentArtifact", "{{consentArtifact}}");
    }
}
