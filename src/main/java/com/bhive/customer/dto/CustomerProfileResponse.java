package com.bhive.customer.dto;

import com.bhive.customer.entity.CustomerProfile;
import java.time.LocalDateTime;

public record CustomerProfileResponse(
    Long id,
    String profileId,
    String name,
    String email,
    String phone,
    String locationAddress,
    Double latitude,
    Double longitude,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CustomerProfileResponse from(CustomerProfile profile) {
        return new CustomerProfileResponse(
            profile.getId(), profile.getProfileId(), profile.getName(), profile.getEmail(), profile.getPhone(),
            profile.getLocationAddress(), profile.getLatitude(), profile.getLongitude(),
            profile.getCreatedAt(), profile.getUpdatedAt()
        );
    }
}