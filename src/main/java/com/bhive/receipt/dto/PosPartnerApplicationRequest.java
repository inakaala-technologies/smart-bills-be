package com.bhive.receipt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PosPartnerApplicationRequest(
    @NotBlank @Size(max = 160) @Pattern(regexp = "^[^\\r\\n]*$") String partnerName,
    @NotBlank @Size(max = 160) @Pattern(regexp = "^[^\\r\\n]*$") String contactName,
    @NotBlank @Email @Size(max = 254) String contactEmail,
    @NotBlank @Size(max = 32) @Pattern(regexp = "^[^\\r\\n]*$") String contactPhone,
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[^\\r\\n]*$") String retailerReference,
    @NotBlank @Size(max = 160) @Pattern(regexp = "^[^\\r\\n]*$") String retailerName,
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[^\\r\\n]*$") String outletReference,
    @NotBlank @Size(max = 160) @Pattern(regexp = "^[^\\r\\n]*$") String outletName,
    @NotBlank @Size(max = 1024) String publicKey,
    @NotBlank @Size(max = 1024) String proofSignature
) {
}
