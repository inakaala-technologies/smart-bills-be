package com.bhive.auth.service;

import com.bhive.auth.entity.RefreshToken;
import com.bhive.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final int REFRESH_TOKEN_DAYS = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String issueRefreshToken(Long userId) {
        return createToken(userId, UUID.randomUUID().toString());
    }

    @Transactional
    public Optional<RotatedRefreshToken> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
        if (storedToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken current = storedToken.get();
        LocalDateTime now = LocalDateTime.now();
        if (current.getRevokedAt() != null) {
            revokeActiveFamily(current.getFamilyId(), now);
            return Optional.empty();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.setRevokedAt(now);
            refreshTokenRepository.save(current);
            return Optional.empty();
        }

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        return Optional.of(new RotatedRefreshToken(
            current.getUserId(), createToken(current.getUserId(), current.getFamilyId())
        ));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token -> {
            LocalDateTime now = LocalDateTime.now();
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
                refreshTokenRepository.save(token);
            }
            revokeActiveFamily(token.getFamilyId(), now);
        });
    }

    private String createToken(Long userId, String familyId) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setFamilyId(familyId);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private void revokeActiveFamily(String familyId, LocalDateTime revokedAt) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
        activeTokens.forEach(token -> token.setRevokedAt(revokedAt));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record RotatedRefreshToken(Long userId, String rawToken) {
    }
}