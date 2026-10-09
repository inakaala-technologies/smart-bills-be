package com.bhive.customer.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record CustomerPageResponse(
    List<CustomerProfileResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static CustomerPageResponse from(Page<CustomerProfileResponse> page) {
        return new CustomerPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}