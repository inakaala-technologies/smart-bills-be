package com.bhive.receipt.dto;

public record ReceiptDeliveryPreferenceRequest(Boolean emailEnabled, Boolean phoneEnabled) {
}
