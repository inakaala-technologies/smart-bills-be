package com.bhive.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
    @NotBlank @Size(max = 120) String name,
    @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 30) String phone
) {
}