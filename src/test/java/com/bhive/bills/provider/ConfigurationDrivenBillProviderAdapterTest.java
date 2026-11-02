package com.bhive.bills.provider;

import com.bhive.bills.config.ApiSetuProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfigurationDrivenBillProviderAdapterTest {

    private static final String BASE_URL = "https://sandbox.api-setu.in";
    private static final String ENDPOINT = "/certificate/v3/kseb/elebl";
    private static final byte[] TEST_PDF_BYTES = new byte[] {
            0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x37, 0x00, (byte) 0xff
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejects_missing_input_before_sending_request() throws Exception {
        TestClient testClient = createTestClient("JSON", "application/json");

        assertThrows(
                IllegalArgumentException.class,
                () -> testClient.adapter.fetchBill("KSEB", Map.of(), consentArtifact())
        );
        testClient.server.verify();
    }

    @Test
    void rejects_missing_consent_before_sending_request() {
        TestClient testClient = createTestClient("JSON", "application/json");

        assertThrows(
                IllegalArgumentException.class,
                () -> testClient.adapter.fetchBill("KSEB", Map.of("ConsumerNo", "123"), null)
        );
        testClient.server.verify();
    }

    @Test
    void reports_upstream_http_error_status_instead_of_returning_success() {
        TestClient testClient = createTestClient("JSON", "application/json");
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> testClient.adapter.fetchBill(
                        "KSEB",
                        Map.of("ConsumerNo", "123"),
                        consentArtifact()
                )
        );

        assertTrue(exception.getMessage().contains("502"));
        testClient.server.verify();
    }

    @Test
    void preserves_json_response_and_records_actual_content_type() throws Exception {
        TestClient testClient = createTestClient("JSON", "application/json");
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess(
                        "{\"status\":\"OK\",\"providerField\":{\"nested\":42}}",
                        MediaType.APPLICATION_JSON
                ));

        ProviderFetchResponse response = testClient.adapter.fetchBill(
                "KSEB",
                Map.of("ConsumerNo", "123"),
                consentArtifact()
        );

        assertEquals("JSON", response.format());
        assertEquals("application/json", response.contentType());
        assertEquals(42, response.jsonData().path("providerField").path("nested").asInt());
        testClient.server.verify();
    }

    @Test
    void preserves_xml_response_as_received() {
        TestClient testClient = createTestClient("XML", "application/xml");
        String xmlResponse = "<?xml version=\"1.0\"?><Certificate><Value>42</Value></Certificate>";
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess(xmlResponse, MediaType.APPLICATION_XML));

        ProviderFetchResponse response = testClient.adapter.fetchBill(
                "KSEB",
                Map.of("ConsumerNo", "123"),
                consentArtifact()
        );

        assertEquals("XML", response.format());
        assertEquals(xmlResponse, response.xmlData());
        assertEquals("application/xml", response.contentType());
        testClient.server.verify();
    }

    @Test
    void preserves_pdf_response_bytes_without_string_conversion() {
        TestClient testClient = createTestClient("PDF", "application/pdf");
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess(TEST_PDF_BYTES, MediaType.APPLICATION_PDF));

        ProviderFetchResponse response = testClient.adapter.fetchBill(
                "KSEB",
                Map.of("ConsumerNo", "123"),
                consentArtifact()
        );

        assertEquals("PDF", response.format());
        assertArrayEquals(TEST_PDF_BYTES, response.pdfData());
        assertEquals("application/pdf", response.contentType());
        testClient.server.verify();
    }

    @Test
    void does_not_decode_base64_text_as_a_pdf() {
        TestClient testClient = createTestClient("PDF", "application/pdf");
        byte[] base64Payload = "JVBERi0xLjQ=".getBytes(StandardCharsets.US_ASCII);
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess(base64Payload, MediaType.APPLICATION_PDF));

        ProviderFetchResponse response = testClient.adapter.fetchBill(
                "KSEB",
                Map.of("ConsumerNo", "123"),
                consentArtifact()
        );

        assertArrayEquals(base64Payload, response.pdfData());
        testClient.server.verify();
    }

    @Test
    void response_parsing_uses_configured_format_not_upstream_content_type() {
        TestClient testClient = createTestClient("JSON", "application/json");
        testClient.server.expect(requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess("<Certificate/>", MediaType.APPLICATION_XML));

        assertThrows(
                IllegalStateException.class,
                () -> testClient.adapter.fetchBill(
                        "KSEB",
                        Map.of("ConsumerNo", "123"),
                        consentArtifact()
                )
        );
        testClient.server.verify();
    }

    private TestClient createTestClient(String responseFormat, String accept) {
        ApiSetuProperties properties = new ApiSetuProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("test-api-key-from-config");
        properties.setClientId("test-client-id-from-config");

        ApiSetuProperties.ProviderConfig providerConfig = new ApiSetuProperties.ProviderConfig();
        providerConfig.setEndpoint(ENDPOINT);
        providerConfig.setHttpMethod("POST");
        providerConfig.setRequestFormat("JSON");
        providerConfig.setResponseFormat(responseFormat);
        providerConfig.setContentType("application/json");
        providerConfig.setAccept(accept);
        providerConfig.setRequestTemplate(Map.of(
                "txnId", "{{txnId}}",
                "format", "xml",
                "certificateParameters", Map.of("ConsumerNo", "{{input.ConsumerNo}}"),
                "consentArtifact", "{{consentArtifact}}"
        ));
        properties.setProviders(Map.of("KSEB", providerConfig));

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ConfigurationDrivenBillProviderAdapter adapter =
                new ConfigurationDrivenBillProviderAdapter(
                        restClient,
                        properties,
                        new ProviderRequestTemplateProcessor(),
                        objectMapper
                );

        return new TestClient(adapter, server);
    }

    private JsonNode consentArtifact() {
        ObjectNode artifact = objectMapper.createObjectNode();
        artifact.putObject("consent")
                .put("consentId", "mock-consent-for-unit-test");
        artifact.putObject("signature")
                .put("signature", "mock-only-not-a-valid-live-signature");
        return artifact;
    }

    private record TestClient(
            ConfigurationDrivenBillProviderAdapter adapter,
            MockRestServiceServer server
    ) {
    }
}
