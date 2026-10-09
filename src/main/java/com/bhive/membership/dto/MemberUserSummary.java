package com.bhive.membership.dto;

public record MemberUserSummary(Long id, String profileId, String name, String email, String maskedPhone) {
}