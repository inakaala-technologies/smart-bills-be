package com.bhive.bills.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApiSetuServiceCatalog {

    private static final List<ServiceDefinition> SERVICES = List.of(
            service("TRANSPORT_ECHALLAN", "Transport E-Challan", "ECHALLAN",
                    "/certificate/v3/transport/chaln", "application/pdf",
                    "REQUEST", "dl_rc", "FullName"),
            service("DOC_DRIVING_LICENSE", "Driving License", "DOCUMENTS",
                    "/certificate/v3/transport/drvlc", "application/pdf",
                    "dlno", "UID", "FullName", "DOB"),
            service("DOC_FITNESS_CERTIFICATE", "Fitness Certificate", "DOCUMENTS",
                    "/certificate/v3/transport/fitcer", "application/pdf",
                    "reg_no", "swd_name", "chasis_no", "UID", "FullName"),
            service("DOC_VEHICLE_REGISTRATION", "Vehicle Registration", "DOCUMENTS",
                    "/certificate/v3/transport/rvcer", "application/pdf",
                    "reg_no", "chasis_no", "UID", "FullName"),
            service("DOC_VEHICLE_TAX", "Vehicle Tax Receipt", "DOCUMENTS",
                    "/certificate/v3/transport/vhtax", "application/pdf",
                    "reg_no", "swd_name", "chasis_no", "UID", "FullName"),
            service("INS_TRANSPORT_VEHICLE", "Vehicle Insurance Certificate", "INSURANCE",
                    "/certificate/v3/transport/vhinsc", "application/pdf",
                    "reg_no", "swd_name", "chasis_no", "UID", "FullName"),

            service("INS_BHARTI_AXA_LIFE", "Bharti AXA Life", "INSURANCE",
                    "/bhartiaxalife/licer", "application/json", "PolicyNumber"),
            service("INS_INDIAFIRST_LIFE", "IndiaFirst Life", "INSURANCE",
                    "/indiafirstlife/podoc", "application/json", "PolicyNo"),
            service("INS_KOTAK_LIFE", "Kotak Life", "INSURANCE",
                    "/kotakinsurance/podoc", "application/json", "PolicyNumber"),
            service("INS_LIBERTY_HEALTH", "Liberty General - Health", "INSURANCE",
                    "/libertyinsurance/hlipc", "application/json", "PolicyNumber", "DateOfBirth"),
            service("INS_LIBERTY_CAR", "Liberty General - Car", "INSURANCE",
                    "/libertyinsurance/cripc", "application/json",
                    "PolicyNumber", "RegistrationNumber", "ChaissNumber"),
            service("INS_LIBERTY_COMMERCIAL_VEHICLE", "Liberty General - Commercial Vehicle", "INSURANCE",
                    "/libertyinsurance/cvipc", "application/json",
                    "PolicyNumber", "RegistrationNumber", "ChaissNumber"),
            service("INS_LIBERTY_TWO_WHEELER", "Liberty General - Two Wheeler", "INSURANCE",
                    "/libertyinsurance/twipc", "application/json",
                    "PolicyNumber", "RegistrationNumber", "ChaissNumber"),
            service("INS_LIBERTY_PERSONAL_ACCIDENT", "Liberty General - Personal Accident", "INSURANCE",
                    "/libertyinsurance/paipc", "application/json", "PolicyNumber", "PinCode"),

            service("INS_NATIONAL_ENGINEERING", "National Insurance - Engineering", "INSURANCE",
                    "/nationalinsurance/egipc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_GROUP", "National Insurance - Group Insurance", "INSURANCE",
                    "/nationalinsurance/gicer", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_HEALTH", "National Insurance - Health", "INSURANCE",
                    "/nationalinsurance/hlipc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_MARINE", "National Insurance - Marine", "INSURANCE",
                    "/nationalinsurance/mripc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_MISCELLANEOUS", "National Insurance - Miscellaneous", "INSURANCE",
                    "/nationalinsurance/miipc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_PROPERTY", "National Insurance - Property", "INSURANCE",
                    "/nationalinsurance/pripc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_CAR", "National Insurance - Car", "INSURANCE",
                    "/nationalinsurance/cripc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_COMMERCIAL_VEHICLE", "National Insurance - Commercial Vehicle", "INSURANCE",
                    "/nationalinsurance/cvipc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_HOME", "National Insurance - Home", "INSURANCE",
                    "/nationalinsurance/hmipc", "application/json", "policy_Number", "policy_StartDate"),
            service("INS_NATIONAL_TWO_WHEELER", "National Insurance - Two Wheeler", "INSURANCE",
                    "/nationalinsurance/twipc", "application/json", "policy_Number", "policy_StartDate"),

            service("INS_GODIGIT_HEALTH", "Go Digit - Health", "INSURANCE",
                    "/godigit/hlipc", "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber"),
            service("INS_GODIGIT_CAR", "Go Digit - Car", "INSURANCE",
                    "/godigit/cripc", "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber"),
            service("INS_GODIGIT_COMMERCIAL_VEHICLE", "Go Digit - Commercial Vehicle", "INSURANCE",
                    "/godigit/cvipc", "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber"),
            service("INS_GODIGIT_TWO_WHEELER", "Go Digit - Two Wheeler", "INSURANCE",
                    "/godigit/twipc", "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber"),
            service("INS_GODIGIT_PERSONAL_ACCIDENT", "Go Digit - Personal Accident", "INSURANCE",
                    "/godigit/paipc", "application/json", "PolicyNumber", "SearchDocument", "DocumentNumber"),
            service("INS_EDELWEISS_LIFE", "Edelweiss Life", "INSURANCE",
                    "/edelweisslife/podoc", "application/json", "PolicyNumber"),

            service("INS_FUTURE_GENERALI_HEALTH", "Future Generali - Health", "INSURANCE",
                    "/futuregenerali/hlipc", "application/json", "policyno"),
            service("INS_FUTURE_GENERALI_TWO_WHEELER", "Future Generali - Two Wheeler", "INSURANCE",
                    "/futuregenerali/twipc", "application/json", "policyno"),
            service("INS_FUTURE_GENERALI_CAR", "Future Generali - Car", "INSURANCE",
                    "/futuregenerali/cripc", "application/json", "policyno"),
            service("INS_FUTURE_GENERALI_COMMERCIAL_VEHICLE", "Future Generali - Commercial Vehicle", "INSURANCE",
                    "/futuregenerali/cvipc", "application/json", "policyno"),
            service("INS_FUTURE_GENERALI_TRAVEL", "Future Generali - Travel", "INSURANCE",
                    "/futuregenerali/tripc", "application/json", "policyno"),
            service("INS_FUTURE_GENERALI_HOME", "Future Generali - Home", "INSURANCE",
                    "/futuregenerali/hmipc", "application/json", "policyno"),
            service("INS_BANDHAN_LIFE", "Bandhan Life", "INSURANCE",
                    "/aegonlife/podoc", "application/json", "policyNumber"),

            service("INS_SBI_HEALTH", "SBI General - Health", "INSURANCE",
                    "/sbigeneral/hlipc", "application/json", "UDF1", "UDF2"),
            service("INS_SBI_CAR", "SBI General - Car", "INSURANCE",
                    "/sbigeneral/cripc", "application/json", "UDF1", "UDF2"),
            service("INS_SBI_COMMERCIAL_VEHICLE", "SBI General - Commercial Vehicle", "INSURANCE",
                    "/sbigeneral/cvipc", "application/json", "UDF1", "UDF2"),
            service("INS_SBI_TRAVEL", "SBI General - Travel", "INSURANCE",
                    "/sbigeneral/tripc", "application/json", "UDF1", "UDF2"),
            service("INS_SBI_TWO_WHEELER", "SBI General - Two Wheeler", "INSURANCE",
                    "/sbigeneral/twipc", "application/json", "UDF1", "UDF2"),
            service("INS_SBI_PERSONAL_ACCIDENT", "SBI General - Personal Accident", "INSURANCE",
                    "/sbigeneral/paipc", "application/json", "UDF2", "UDF1"),

            service("INS_HDFC_ERGO_HEALTH", "HDFC ERGO - Health", "INSURANCE",
                    "/hdfcergogi/hlipc", "application/json", "PolicyNo", "MobileNo"),
            service("INS_HDFC_ERGO_CAR", "HDFC ERGO - Car", "INSURANCE",
                    "/hdfcergogi/cripc", "application/json", "PolicyNo", "MobileNo"),
            service("INS_HDFC_ERGO_TWO_WHEELER", "HDFC ERGO - Two Wheeler", "INSURANCE",
                    "/hdfcergogi/twipc", "application/json", "PolicyNo", "MobileNo"),
            service("INS_CARE_HEALTH", "Care Health", "INSURANCE",
                    "/careinsurance/podoc", "application/json", "policyNum"),
            service("INS_BAJAJ_ALLIANZ_LIFE", "Bajaj Allianz Life", "INSURANCE",
                    "/bajajallianzlife/licer", "application/json", "PolicyNumber"),
            service("INS_PNB_METLIFE", "PNB MetLife", "INSURANCE",
                    "/pnbmetlife/podoc", "application/json",
                    "PMLIPolicyNo", "PMLIApplicationNo", "PMLINameAsPerPolicy"),
            service("INS_CANARA_HSBC_LIFE", "Canara HSBC Life", "INSURANCE",
                    "/canarahsbclife/licer", "application/json", "PolicyNo"),

            service("INS_MAGMA_HEALTH", "Magma HDI - Health", "INSURANCE",
                    "/magmahdi/hlipc", "application/json", "policy_number", "DateOfBirth"),
            service("INS_MAGMA_MISCELLANEOUS", "Magma HDI - Miscellaneous", "INSURANCE",
                    "/magmahdi/miipc", "application/json", "policy_number", "Pincode"),
            service("INS_MAGMA_CAR", "Magma HDI - Car", "INSURANCE",
                    "/magmahdi/cripc", "application/json", "policy_number", "RegistrationNumber"),
            service("INS_MAGMA_COMMERCIAL_VEHICLE", "Magma HDI - Commercial Vehicle", "INSURANCE",
                    "/magmahdi/cvipc", "application/json", "policy_number", "RegistrationNumber"),
            service("INS_MAGMA_HOME", "Magma HDI - Home", "INSURANCE",
                    "/magmahdi/hmipc", "application/json", "policy_number", "Pincode"),
            service("INS_MAGMA_TWO_WHEELER", "Magma HDI - Two Wheeler", "INSURANCE",
                    "/magmahdi/twipc", "application/json", "policy_number", "RegistrationNumber"),

            service("INS_ICICI_LOMBARD_HEALTH", "ICICI Lombard - Health", "INSURANCE",
                    "/icicilombard/hlipc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_ICICI_LOMBARD_CAR", "ICICI Lombard - Car", "INSURANCE",
                    "/icicilombard/cripc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_ICICI_LOMBARD_COMMERCIAL_VEHICLE", "ICICI Lombard - Commercial Vehicle", "INSURANCE",
                    "/icicilombard/cvipc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_ICICI_LOMBARD_HOME", "ICICI Lombard - Home", "INSURANCE",
                    "/icicilombard/hmipc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_ICICI_LOMBARD_TRAVEL", "ICICI Lombard - Travel", "INSURANCE",
                    "/icicilombard/tripc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_ICICI_LOMBARD_TWO_WHEELER", "ICICI Lombard - Two Wheeler", "INSURANCE",
                    "/icicilombard/twipc", "application/json", "PolicyNo", "InsuredDob"),
            service("INS_PMFBY_CROP", "PMFBY Crop Insurance", "INSURANCE",
                    "/pmfby/podoc", "application/json", "year", "seasonCode"),
            service("INS_RAHEJA_QBE_CAR", "Raheja QBE - Car", "INSURANCE",
                    "/rahejaqbe/cripc", "application/json",
                    "ipMobile", "ipPolicyNo", "ipRECNo", "ipRECNoValue"),
            service("INS_RAHEJA_QBE_HEALTH", "Raheja QBE - Health", "INSURANCE",
                    "/rahejaqbe/hlipc", "application/json", "ipInsuredDOB", "ipPolicyNo"),
            service("INS_ADITYA_BIRLA_SUN_LIFE", "Aditya Birla Sun Life", "INSURANCE",
                    "/abcapital/podoc", "application/json", "PolicyNumber", "InsuredDOB")
    );

    private ApiSetuServiceCatalog() {
    }

    public static List<ServiceDefinition> services() {
        return SERVICES;
    }

    public static Optional<ServiceDefinition> find(String providerCode) {
        return SERVICES.stream()
                .filter(service -> service.code().equalsIgnoreCase(providerCode))
                .findFirst();
    }

    public static void registerProviderConfigurations(ApiSetuProperties properties) {
        if (properties.getProviders() == null) {
            properties.setProviders(new LinkedHashMap<>());
        }
        for (ServiceDefinition service : SERVICES) {
            properties.getProviders().putIfAbsent(
                    service.code(),
                    providerConfiguration(service)
            );
        }
    }

    private static ApiSetuProperties.ProviderConfig providerConfiguration(
            ServiceDefinition service
    ) {
        ApiSetuProperties.ProviderConfig config = new ApiSetuProperties.ProviderConfig();
        config.setEndpoint(service.endpoint());
        config.setHttpMethod("POST");
        config.setRequestFormat("JSON");
        config.setResponseFormat("application/pdf".equals(service.accept()) ? "PDF" : "JSON");
        config.setContentType("application/json");
        config.setAccept(service.accept());

        Map<String, Object> certificateParameters = new LinkedHashMap<>();
        for (String field : service.inputFields()) {
            certificateParameters.put(field, "{{input." + field + "}}");
        }
        Map<String, Object> requestTemplate = new LinkedHashMap<>();
        requestTemplate.put("txnId", "{{txnId}}");
        requestTemplate.put("format", "xml");
        requestTemplate.put("certificateParameters", certificateParameters);
        requestTemplate.put("consentArtifact", "{{consentArtifact}}");
        config.setRequestTemplate(requestTemplate);
        return config;
    }

    private static ServiceDefinition service(
            String code,
            String name,
            String categoryCode,
            String endpoint,
            String accept,
            String... inputFields
    ) {
        return new ServiceDefinition(
                code,
                name,
                categoryCode,
                endpoint,
                accept,
                List.of(inputFields)
        );
    }

    public record ServiceDefinition(
            String code,
            String name,
            String categoryCode,
            String endpoint,
            String accept,
            List<String> inputFields
    ) {
        public ServiceDefinition {
            inputFields = List.copyOf(inputFields);
        }
    }
}
