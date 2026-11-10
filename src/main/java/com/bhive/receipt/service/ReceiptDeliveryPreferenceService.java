package com.bhive.receipt.service;

import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.receipt.dto.ReceiptDeliveryPreferenceResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiptDeliveryPreferenceService {

    private final CustomerProfileRepository profileRepository;
    private final boolean allowUnverifiedContactMatching;

    public ReceiptDeliveryPreferenceService(
        CustomerProfileRepository profileRepository,
        @Value("${app.receipts.allow-unverified-contact-matching:false}") boolean allowUnverifiedContactMatching
    ) {
        this.profileRepository = profileRepository;
        this.allowUnverifiedContactMatching = allowUnverifiedContactMatching;
    }

    @Transactional(readOnly = true)
    public ReceiptDeliveryPreferenceResponse getPreference(Long userId, Long tenantId) {
        return toResponse(findOwnProfile(userId, tenantId));
    }

    @Transactional
    public ReceiptDeliveryPreferenceResponse updatePreference(
        Long userId,
        Long tenantId,
        boolean emailEnabled,
        boolean phoneEnabled
    ) {
        validateIdentity(userId, tenantId);
        CustomerProfile profile = profileRepository.findByUserIdAndTenantIdForUpdate(userId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile is not available."));
        if ((emailEnabled || phoneEnabled) && !allowUnverifiedContactMatching) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Contact receipt matching is disabled until contact verification is enabled."
            );
        }
        if (emailEnabled && !isValidEmail(profile.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a valid email address to your customer profile first.");
        }
        if (emailEnabled) {
            profile.setEmail(profile.getEmail());
        }
        if (phoneEnabled) {
            String normalizedPhone = normalizePhone(profile.getPhone());
            if (normalizedPhone == null || normalizedPhone.length() < 8 || normalizedPhone.length() > 15) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a valid mobile number to your customer profile first.");
            }
            profile.setPhone(profile.getPhone());
        }
        profile.setReceiptEmailEnabled(emailEnabled);
        profile.setReceiptPhoneEnabled(phoneEnabled);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<ReceiptRecipient> findOptedInRecipientsByEmail(String email) {
        requireContactMatchingEnabled();
        if (!isValidEmail(email)) {
            return List.of();
        }
        List<CustomerProfile> matches = profileRepository
            .findByEmailNormalizedAndReceiptEmailEnabledTrueAndUserIdIsNotNull(
                email.trim().toLowerCase(java.util.Locale.ROOT)
            );
        return resolveUniqueRecipients(matches);
    }

    @Transactional(readOnly = true)
    public List<ReceiptRecipient> findOptedInRecipientsByPhone(String phone) {
        requireContactMatchingEnabled();
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone == null || normalizedPhone.length() < 8 || normalizedPhone.length() > 15) {
            return List.of();
        }
        List<CustomerProfile> matches = profileRepository
            .findByPhoneNormalizedAndReceiptPhoneEnabledTrueAndUserIdIsNotNull(normalizedPhone);
        return resolveUniqueRecipients(matches);
    }

    private void requireContactMatchingEnabled() {
        if (!allowUnverifiedContactMatching) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Contact-based receipt matching is currently disabled."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ReceiptRecipient> findRecipientsByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return profileRepository.findByUserIdIn(List.of(userId)).stream()
            .filter(profile -> profile.getTenantId() != null)
            .map(profile -> new ReceiptRecipient(userId, profile.getTenantId()))
            .distinct()
            .toList();
    }

    private CustomerProfile findOwnProfile(Long userId, Long tenantId) {
        validateIdentity(userId, tenantId);
        return profileRepository.findByUserIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile is not available."));
    }

    private void validateIdentity(Long userId, Long tenantId) {
        if (userId == null || userId <= 0 || tenantId == null || tenantId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An authenticated customer is required.");
        }
    }

    private List<ReceiptRecipient> resolveUniqueRecipients(List<CustomerProfile> profiles) {
        List<Long> userIds = profiles.stream()
            .map(CustomerProfile::getUserId)
            .filter(userId -> userId != null)
            .distinct()
            .toList();
        if (userIds.size() != 1) {
            return List.of();
        }
        Long userId = userIds.get(0);
        return profiles.stream()
            .filter(profile -> userId.equals(profile.getUserId()) && profile.getTenantId() != null)
            .map(profile -> new ReceiptRecipient(userId, profile.getTenantId()))
            .distinct()
            .toList();
    }

    private ReceiptDeliveryPreferenceResponse toResponse(CustomerProfile profile) {
        String normalizedPhone = normalizePhone(profile.getPhone());
        return new ReceiptDeliveryPreferenceResponse(
            allowUnverifiedContactMatching,
            isValidEmail(profile.getEmail()),
            normalizedPhone != null && normalizedPhone.length() >= 8 && normalizedPhone.length() <= 15,
            profile.isReceiptEmailEnabled(),
            profile.isReceiptPhoneEnabled()
        );
    }

    private boolean isValidEmail(String email) {
        return email != null && email.trim().length() <= 254
            && email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("[^0-9]", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
