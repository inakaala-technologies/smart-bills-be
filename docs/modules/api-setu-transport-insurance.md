# API Setu Transport and Insurance Catalog

The transport and insurance services are defined in
`com.bhive.bills.config.ApiSetuServiceCatalog`. That catalog supplies both the
existing provider-input catalog and the configuration-driven adapter. Provider
codes are independent per endpoint/policy service. Input names preserve the
case and spelling from the supplied API Setu specifications.

All entries use `POST`, JSON request and `Content-Type: application/json`.
Requests contain `txnId`, `format: "xml"`, the listed
`certificateParameters`, and `consentArtifact`. API Setu credentials are taken
from the existing shared configuration; no sample credentials or consumer
values are stored in the catalog.

## Catalog initialization

On application startup, `BillCatalogInitializer` synchronizes provider and
input-field rows by their stable category/provider/field codes. It seeds the
66 services in this catalog alongside the existing 11 electricity and water
providers. The API Setu request templates remain code/property configuration
generated from this catalog; they are not stored in `bill_providers` or
`provider_input_fields`. The catalog API reads provider and field definitions
from those database tables.

For a database containing only the existing utility catalog, initialization
adds 61 INSURANCE providers, one ECHALLAN provider, four DOCUMENTS providers,
and 143 service input fields. Together with the existing utility definitions,
the expected total is 77 providers and 161 input fields. Re-running startup
does not create duplicate rows.

## Transport services

| Category | Provider code | Endpoint | Accept | `certificateParameters` |
|---|---|---|---|---|
| ECHALLAN | `TRANSPORT_ECHALLAN` | `/certificate/v3/transport/chaln` | `application/pdf` | `REQUEST`, `dl_rc`, `FullName` |
| DOCUMENTS | `DOC_DRIVING_LICENSE` | `/certificate/v3/transport/drvlc` | `application/pdf` | `dlno`, `UID`, `FullName`, `DOB` |
| DOCUMENTS | `DOC_FITNESS_CERTIFICATE` | `/certificate/v3/transport/fitcer` | `application/pdf` | `reg_no`, `swd_name`, `chasis_no`, `UID`, `FullName` |
| DOCUMENTS | `DOC_VEHICLE_REGISTRATION` | `/certificate/v3/transport/rvcer` | `application/pdf` | `reg_no`, `chasis_no`, `UID`, `FullName` |
| DOCUMENTS | `DOC_VEHICLE_TAX` | `/certificate/v3/transport/vhtax` | `application/pdf` | `reg_no`, `swd_name`, `chasis_no`, `UID`, `FullName` |
| INSURANCE | `INS_TRANSPORT_VEHICLE` | `/certificate/v3/transport/vhinsc` | `application/pdf` | `reg_no`, `swd_name`, `chasis_no`, `UID`, `FullName` |

## Insurance services

Each row is a separately registered service. All use `Accept: application/json`.

| Insurer / service | Provider code | Endpoint | `certificateParameters` |
|---|---|---|---|
| Bharti AXA Life | `INS_BHARTI_AXA_LIFE` | `/bhartiaxalife/licer` | `PolicyNumber` |
| IndiaFirst Life | `INS_INDIAFIRST_LIFE` | `/indiafirstlife/podoc` | `PolicyNo` |
| Kotak Life | `INS_KOTAK_LIFE` | `/kotakinsurance/podoc` | `PolicyNumber` |
| Liberty General - Health | `INS_LIBERTY_HEALTH` | `/libertyinsurance/hlipc` | `PolicyNumber`, `DateOfBirth` |
| Liberty General - Car | `INS_LIBERTY_CAR` | `/libertyinsurance/cripc` | `PolicyNumber`, `RegistrationNumber`, `ChaissNumber` |
| Liberty General - Commercial Vehicle | `INS_LIBERTY_COMMERCIAL_VEHICLE` | `/libertyinsurance/cvipc` | `PolicyNumber`, `RegistrationNumber`, `ChaissNumber` |
| Liberty General - Two Wheeler | `INS_LIBERTY_TWO_WHEELER` | `/libertyinsurance/twipc` | `PolicyNumber`, `RegistrationNumber`, `ChaissNumber` |
| Liberty General - Personal Accident | `INS_LIBERTY_PERSONAL_ACCIDENT` | `/libertyinsurance/paipc` | `PolicyNumber`, `PinCode` |
| National Insurance - Engineering | `INS_NATIONAL_ENGINEERING` | `/nationalinsurance/egipc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Group | `INS_NATIONAL_GROUP` | `/nationalinsurance/gicer` | `policy_Number`, `policy_StartDate` |
| National Insurance - Health | `INS_NATIONAL_HEALTH` | `/nationalinsurance/hlipc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Marine | `INS_NATIONAL_MARINE` | `/nationalinsurance/mripc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Miscellaneous | `INS_NATIONAL_MISCELLANEOUS` | `/nationalinsurance/miipc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Property | `INS_NATIONAL_PROPERTY` | `/nationalinsurance/pripc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Car | `INS_NATIONAL_CAR` | `/nationalinsurance/cripc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Commercial Vehicle | `INS_NATIONAL_COMMERCIAL_VEHICLE` | `/nationalinsurance/cvipc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Home | `INS_NATIONAL_HOME` | `/nationalinsurance/hmipc` | `policy_Number`, `policy_StartDate` |
| National Insurance - Two Wheeler | `INS_NATIONAL_TWO_WHEELER` | `/nationalinsurance/twipc` | `policy_Number`, `policy_StartDate` |
| Go Digit - Health | `INS_GODIGIT_HEALTH` | `/godigit/hlipc` | `PolicyNumber`, `SearchDocument`, `DocumentNumber` |
| Go Digit - Car | `INS_GODIGIT_CAR` | `/godigit/cripc` | `PolicyNumber`, `SearchDocument`, `DocumentNumber` |
| Go Digit - Commercial Vehicle | `INS_GODIGIT_COMMERCIAL_VEHICLE` | `/godigit/cvipc` | `PolicyNumber`, `SearchDocument`, `DocumentNumber` |
| Go Digit - Two Wheeler | `INS_GODIGIT_TWO_WHEELER` | `/godigit/twipc` | `PolicyNumber`, `SearchDocument`, `DocumentNumber` |
| Go Digit - Personal Accident | `INS_GODIGIT_PERSONAL_ACCIDENT` | `/godigit/paipc` | `PolicyNumber`, `SearchDocument`, `DocumentNumber` |
| Edelweiss Life | `INS_EDELWEISS_LIFE` | `/edelweisslife/podoc` | `PolicyNumber` |
| Future Generali - Health | `INS_FUTURE_GENERALI_HEALTH` | `/futuregenerali/hlipc` | `policyno` |
| Future Generali - Two Wheeler | `INS_FUTURE_GENERALI_TWO_WHEELER` | `/futuregenerali/twipc` | `policyno` |
| Future Generali - Car | `INS_FUTURE_GENERALI_CAR` | `/futuregenerali/cripc` | `policyno` |
| Future Generali - Commercial Vehicle | `INS_FUTURE_GENERALI_COMMERCIAL_VEHICLE` | `/futuregenerali/cvipc` | `policyno` |
| Future Generali - Travel | `INS_FUTURE_GENERALI_TRAVEL` | `/futuregenerali/tripc` | `policyno` |
| Future Generali - Home | `INS_FUTURE_GENERALI_HOME` | `/futuregenerali/hmipc` | `policyno` |
| Bandhan Life | `INS_BANDHAN_LIFE` | `/aegonlife/podoc` | `policyNumber` |
| SBI General - Health | `INS_SBI_HEALTH` | `/sbigeneral/hlipc` | `UDF1`, `UDF2` |
| SBI General - Car | `INS_SBI_CAR` | `/sbigeneral/cripc` | `UDF1`, `UDF2` |
| SBI General - Commercial Vehicle | `INS_SBI_COMMERCIAL_VEHICLE` | `/sbigeneral/cvipc` | `UDF1`, `UDF2` |
| SBI General - Travel | `INS_SBI_TRAVEL` | `/sbigeneral/tripc` | `UDF1`, `UDF2` |
| SBI General - Two Wheeler | `INS_SBI_TWO_WHEELER` | `/sbigeneral/twipc` | `UDF1`, `UDF2` |
| SBI General - Personal Accident | `INS_SBI_PERSONAL_ACCIDENT` | `/sbigeneral/paipc` | `UDF2`, `UDF1` |
| HDFC ERGO - Health | `INS_HDFC_ERGO_HEALTH` | `/hdfcergogi/hlipc` | `PolicyNo`, `MobileNo` |
| HDFC ERGO - Car | `INS_HDFC_ERGO_CAR` | `/hdfcergogi/cripc` | `PolicyNo`, `MobileNo` |
| HDFC ERGO - Two Wheeler | `INS_HDFC_ERGO_TWO_WHEELER` | `/hdfcergogi/twipc` | `PolicyNo`, `MobileNo` |
| Care Health | `INS_CARE_HEALTH` | `/careinsurance/podoc` | `policyNum` |
| Bajaj Allianz Life | `INS_BAJAJ_ALLIANZ_LIFE` | `/bajajallianzlife/licer` | `PolicyNumber` |
| PNB MetLife | `INS_PNB_METLIFE` | `/pnbmetlife/podoc` | `PMLIPolicyNo`, `PMLIApplicationNo`, `PMLINameAsPerPolicy` |
| Canara HSBC Life | `INS_CANARA_HSBC_LIFE` | `/canarahsbclife/licer` | `PolicyNo` |
| Magma HDI - Health | `INS_MAGMA_HEALTH` | `/magmahdi/hlipc` | `policy_number`, `DateOfBirth` |
| Magma HDI - Miscellaneous | `INS_MAGMA_MISCELLANEOUS` | `/magmahdi/miipc` | `policy_number`, `Pincode` |
| Magma HDI - Car | `INS_MAGMA_CAR` | `/magmahdi/cripc` | `policy_number`, `RegistrationNumber` |
| Magma HDI - Commercial Vehicle | `INS_MAGMA_COMMERCIAL_VEHICLE` | `/magmahdi/cvipc` | `policy_number`, `RegistrationNumber` |
| Magma HDI - Home | `INS_MAGMA_HOME` | `/magmahdi/hmipc` | `policy_number`, `Pincode` |
| Magma HDI - Two Wheeler | `INS_MAGMA_TWO_WHEELER` | `/magmahdi/twipc` | `policy_number`, `RegistrationNumber` |
| ICICI Lombard - Health | `INS_ICICI_LOMBARD_HEALTH` | `/icicilombard/hlipc` | `PolicyNo`, `InsuredDob` |
| ICICI Lombard - Car | `INS_ICICI_LOMBARD_CAR` | `/icicilombard/cripc` | `PolicyNo`, `InsuredDob` |
| ICICI Lombard - Commercial Vehicle | `INS_ICICI_LOMBARD_COMMERCIAL_VEHICLE` | `/icicilombard/cvipc` | `PolicyNo`, `InsuredDob` |
| ICICI Lombard - Home | `INS_ICICI_LOMBARD_HOME` | `/icicilombard/hmipc` | `PolicyNo`, `InsuredDob` |
| ICICI Lombard - Travel | `INS_ICICI_LOMBARD_TRAVEL` | `/icicilombard/tripc` | `PolicyNo`, `InsuredDob` |
| ICICI Lombard - Two Wheeler | `INS_ICICI_LOMBARD_TWO_WHEELER` | `/icicilombard/twipc` | `PolicyNo`, `InsuredDob` |
| PMFBY Crop Insurance | `INS_PMFBY_CROP` | `/pmfby/podoc` | `year`, `seasonCode` |
| Raheja QBE - Car | `INS_RAHEJA_QBE_CAR` | `/rahejaqbe/cripc` | `ipMobile`, `ipPolicyNo`, `ipRECNo`, `ipRECNoValue` |
| Raheja QBE - Health | `INS_RAHEJA_QBE_HEALTH` | `/rahejaqbe/hlipc` | `ipInsuredDOB`, `ipPolicyNo` |
| Aditya Birla Sun Life | `INS_ADITYA_BIRLA_SUN_LIFE` | `/abcapital/podoc` | `PolicyNumber`, `InsuredDOB` |

## Fetch and consent boundaries

Manual fetches use the existing `/api/v1/bills/fetch` flow and save inputs and
responses in `saved_bills`. Insurance and DOCUMENTS remain manual-only. The
automatic scheduler query includes only ELECTRICITY, WATER, and ECHALLAN.

API Setu example consent values in the specifications are placeholders, not
proof of recurring authorization. The current default consent provider returns
no artifact, so scheduled calls fail closed. For ECHALLAN, the consent provider
must additionally affirmatively validate recurring access; the interface
defaults that decision to false. A real consent source must validate the signed
artifact and its permissions before returning an artifact or allowing recurring
access. No real API request is made by tests.

The current database tables and fetch-state columns support these catalog
entries and JSON/PDF results. This implementation requires no migration. Consent
artifacts are not persisted by this change; until a trusted consent provider is
integrated, ECHALLAN scheduled fetches remain deferred.
