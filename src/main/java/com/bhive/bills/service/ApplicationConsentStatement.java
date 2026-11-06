package com.bhive.bills.service;

public final class ApplicationConsentStatement {

    public static final String VERSION = "1";
    public static final String PURPOSE_CODE = "MANUAL_BILL_FETCH";
    public static final String TEXT = """
            InGyro Smart Bills – Customer Consent

            I voluntarily authorize InGyro to retrieve and process the information requested for the bill, document, insurance, or vehicle-related service I have selected.

            I understand and acknowledge that:

            1. The information I provide in the form will be used to identify and retrieve the requested records from the relevant service provider or authorized data source.
            2. My requested information may be transmitted to the relevant provider or authorized service through InGyro's backend integration.
            3. The retrieved information will be displayed to me within the InGyro platform and may be stored in my account's saved records, where applicable.
            4. My information will be processed only for the purpose of fulfilling this specific request and related service operations.
            5. This consent applies only to the current manual request. It does not authorize recurring, scheduled, or automatic data retrieval.
            6. I understand that this InGyro consent acknowledgement is separate from any additional consent or authorization required by the external provider or API Setu.
            7. I can choose not to proceed. If I do not provide consent, the requested retrieval will not be initiated.

            I confirm that I have read and understood this information and voluntarily authorize InGyro to process my information for the selected request.""";

    private ApplicationConsentStatement() {
    }
}
