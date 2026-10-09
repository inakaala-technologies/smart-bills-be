package com.bhive.business.dto;

import java.time.LocalDateTime;
import com.bhive.business.entity.CustomerSegment;

public record BusinessOfferRequest(
    String title,
    String description,
    String discountLabel,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    CustomerSegment targetSegment
) {}