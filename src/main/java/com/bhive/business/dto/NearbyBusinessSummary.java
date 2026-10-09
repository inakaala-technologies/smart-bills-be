package com.bhive.business.dto;

public record NearbyBusinessSummary(
    Long id,
    String name,
    String businessType,
    String address,
    Double latitude,
    Double longitude,
    Double distanceKm
) {
}