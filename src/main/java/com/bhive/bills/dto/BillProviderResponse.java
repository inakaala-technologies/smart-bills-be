package com.bhive.bills.dto;

public record BillProviderResponse(
    Long id,
    String code,
    String name,
    String categoryCode,
    String categoryName
) {}