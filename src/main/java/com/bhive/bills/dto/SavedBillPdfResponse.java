package com.bhive.bills.dto;

public record SavedBillPdfResponse(
        String contentType,
        byte[] data
) {
}
