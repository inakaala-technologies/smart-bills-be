package com.bhive.customer.dto;

public record LocationUpdateRequest(String country, String region, String city, String locationAddress, Double latitude, Double longitude) {
}