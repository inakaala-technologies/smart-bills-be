package com.bhive.receipt.dto;

public record ReceiptDeliveryPreferenceResponse(
    boolean contactMatchingEnabled,
    boolean emailAvailable,
    boolean phoneAvailable,
    boolean emailEnabled,
    boolean phoneEnabled
) {
}
