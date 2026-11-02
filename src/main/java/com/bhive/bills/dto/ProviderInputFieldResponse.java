package com.bhive.bills.dto;

public record ProviderInputFieldResponse(
        String fieldKey,
        String label,
        String inputType,
        boolean required,
        String placeholder,
        String helpText,
        Integer displayOrder
) {
}