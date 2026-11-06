package com.bhive.bills.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecordCustomerBillConsentRequest(
        @NotBlank String providerCode,
        @NotEmpty List<@NotBlank String> requestedDataFields,
        @NotBlank String purposeCode,
        @NotBlank String statementVersion,
        @NotNull Boolean accepted,
        Long savedBillId
) {
}
