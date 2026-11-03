package com.bhive.bills.provider;

import org.springframework.http.HttpStatusCode;

public class ProviderApiException extends IllegalStateException {

    private final int statusCode;
    private final boolean temporary;

    public ProviderApiException(HttpStatusCode statusCode, String providerCode) {
        super("Provider API returned HTTP status " + statusCode.value()
                + " for provider " + providerCode);
        this.statusCode = statusCode.value();
        this.temporary = statusCode.is5xxServerError() || this.statusCode == 429;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isTemporary() {
        return temporary;
    }
}
