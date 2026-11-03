package com.bhive.bills.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiSetuServiceCatalogTest {

    @Test
    void registers_all_documented_transport_and_insurance_services_separately() {
        List<ApiSetuServiceCatalog.ServiceDefinition> services =
                ApiSetuServiceCatalog.services();
        Map<String, ApiSetuServiceCatalog.ServiceDefinition> byCode = services.stream()
                .collect(Collectors.toMap(
                        ApiSetuServiceCatalog.ServiceDefinition::code,
                        service -> service
                ));

        assertEquals(66, services.size());
        assertEquals(61, services.stream()
                .filter(service -> "INSURANCE".equals(service.categoryCode()))
                .count());
        assertEquals(4, services.stream()
                .filter(service -> "DOCUMENTS".equals(service.categoryCode()))
                .count());
        assertEquals(1, services.stream()
                .filter(service -> "ECHALLAN".equals(service.categoryCode()))
                .count());

        assertService(byCode, "TRANSPORT_ECHALLAN", "/certificate/v3/transport/chaln",
                "application/pdf", "REQUEST", "dl_rc", "FullName");
        assertService(byCode, "DOC_DRIVING_LICENSE", "/certificate/v3/transport/drvlc",
                "application/pdf", "dlno", "UID", "FullName", "DOB");
        assertService(byCode, "DOC_FITNESS_CERTIFICATE", "/certificate/v3/transport/fitcer",
                "application/pdf", "reg_no", "swd_name", "chasis_no", "UID", "FullName");
        assertService(byCode, "DOC_VEHICLE_REGISTRATION", "/certificate/v3/transport/rvcer",
                "application/pdf", "reg_no", "chasis_no", "UID", "FullName");
        assertService(byCode, "DOC_VEHICLE_TAX", "/certificate/v3/transport/vhtax",
                "application/pdf", "reg_no", "swd_name", "chasis_no", "UID", "FullName");
        assertService(byCode, "INS_TRANSPORT_VEHICLE", "/certificate/v3/transport/vhinsc",
                "application/pdf", "reg_no", "swd_name", "chasis_no", "UID", "FullName");
        assertService(byCode, "INS_LIBERTY_CAR", "/libertyinsurance/cripc",
                "application/json", "PolicyNumber", "RegistrationNumber", "ChaissNumber");
        assertService(byCode, "INS_NATIONAL_HEALTH", "/nationalinsurance/hlipc",
                "application/json", "policy_Number", "policy_StartDate");
        assertService(byCode, "INS_GODIGIT_HEALTH", "/godigit/hlipc",
                "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber");
        assertService(byCode, "INS_SBI_PERSONAL_ACCIDENT", "/sbigeneral/paipc",
                "application/json", "UDF2", "UDF1");
        assertService(byCode, "INS_MAGMA_HOME", "/magmahdi/hmipc",
                "application/json", "policy_number", "Pincode");
        assertService(byCode, "INS_ICICI_LOMBARD_TRAVEL", "/icicilombard/tripc",
                "application/json", "PolicyNo", "InsuredDob");
        assertService(byCode, "INS_PMFBY_CROP", "/pmfby/podoc",
                "application/json", "year", "seasonCode");
        assertService(byCode, "INS_RAHEJA_QBE_CAR", "/rahejaqbe/cripc",
                "application/json", "ipMobile", "ipPolicyNo", "ipRECNo", "ipRECNoValue");
        assertService(byCode, "INS_ADITYA_BIRLA_SUN_LIFE", "/abcapital/podoc",
                "application/json", "PolicyNumber", "InsuredDOB");
    }

    @Test
    void generated_configuration_uses_exact_dynamic_certificate_fields_and_headers() throws Exception {
        ApiSetuProperties properties = new ApiSetuProperties();
        properties.setProviders(new HashMap<>());
        ApiSetuServiceCatalog.registerProviderConfigurations(properties);

        ApiSetuProperties.ProviderConfig challan =
                properties.getProviders().get("TRANSPORT_ECHALLAN");
        assertEquals("/certificate/v3/transport/chaln", challan.getEndpoint());
        assertEquals("POST", challan.getHttpMethod());
        assertEquals("JSON", challan.getRequestFormat());
        assertEquals("PDF", challan.getResponseFormat());
        assertEquals("application/json", challan.getContentType());
        assertEquals("application/pdf", challan.getAccept());

        assertEquals("{{txnId}}", challan.getRequestTemplate().get("txnId"));
        assertEquals("xml", challan.getRequestTemplate().get("format"));
        assertEquals("{{consentArtifact}}",
                challan.getRequestTemplate().get("consentArtifact"));
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>)
                challan.getRequestTemplate().get("certificateParameters");
        assertEquals(Map.of(
                "REQUEST", "{{input.REQUEST}}",
                "dl_rc", "{{input.dl_rc}}",
                "FullName", "{{input.FullName}}"
        ), parameters);

        ApiSetuProperties.ProviderConfig insurance =
                properties.getProviders().get("INS_NATIONAL_HEALTH");
        assertEquals("application/json", insurance.getAccept());
        @SuppressWarnings("unchecked")
        Map<String, Object> insuranceParameters = (Map<String, Object>)
                insurance.getRequestTemplate().get("certificateParameters");
        assertEquals(List.of("policy_Number", "policy_StartDate"),
                List.copyOf(insuranceParameters.keySet()));
        assertFalse(insuranceParameters.containsKey("policy_number"));
    }

    @Test
    void generates_matching_api_configuration_for_every_catalog_service() {
        ApiSetuProperties properties = new ApiSetuProperties();
        properties.setProviders(new HashMap<>());
        ApiSetuServiceCatalog.registerProviderConfigurations(properties);

        assertEquals(66, properties.getProviders().size());
        for (ApiSetuServiceCatalog.ServiceDefinition service
                : ApiSetuServiceCatalog.services()) {
            ApiSetuProperties.ProviderConfig config =
                    properties.getProviders().get(service.code());
            assertNotNull(config, service.code());
            assertEquals(service.endpoint(), config.getEndpoint(), service.code());
            assertEquals("POST", config.getHttpMethod(), service.code());
            assertEquals("JSON", config.getRequestFormat(), service.code());
            assertEquals("application/json", config.getContentType(), service.code());
            assertEquals(service.accept(), config.getAccept(), service.code());
            assertEquals(
                    "application/pdf".equals(service.accept()) ? "PDF" : "JSON",
                    config.getResponseFormat(),
                    service.code()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>)
                    config.getRequestTemplate().get("certificateParameters");
            assertEquals(service.inputFields(), List.copyOf(parameters.keySet()), service.code());
            for (String field : service.inputFields()) {
                assertEquals(
                        "{{input." + field + "}}",
                        parameters.get(field),
                        service.code() + "." + field
                );
            }
        }
    }

    private void assertService(
            Map<String, ApiSetuServiceCatalog.ServiceDefinition> services,
            String code,
            String endpoint,
            String accept,
            String... fields
    ) {
        ApiSetuServiceCatalog.ServiceDefinition service = services.get(code);
        assertNotNull(service, code);
        assertEquals(endpoint, service.endpoint(), code);
        assertEquals(accept, service.accept(), code);
        assertEquals(List.of(fields), service.inputFields(), code);
    }
}
