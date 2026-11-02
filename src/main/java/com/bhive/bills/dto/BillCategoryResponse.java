package com.bhive.bills.dto;

public record BillCategoryResponse(
    Long id,
    String code,
    String name
) {}