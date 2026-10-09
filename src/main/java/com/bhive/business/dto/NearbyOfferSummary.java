package com.bhive.business.dto;

import java.time.LocalDateTime;

public record NearbyOfferSummary(
    Long id,
    Long businessId,
    String businessName,
    String address,
    String title,
    String description,
    String discountLabel,
    LocalDateTime endsAt,
    double distanceKm
) {}