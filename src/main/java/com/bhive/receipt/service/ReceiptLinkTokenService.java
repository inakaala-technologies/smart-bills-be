package com.bhive.receipt.service;

import com.bhive.receipt.dto.ReceiptLinkTokenResponse;
import com.bhive.receipt.entity.ReceiptLinkToken;
import com.bhive.receipt.repository.ReceiptLinkTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptLinkTokenService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final Duration EXPIRED_TOKEN_RETENTION = Duration.ofHours(24);
    private static final String TOKEN_DOMAIN = "BHIVE_RECEIPT_LINK_V1:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReceiptLinkTokenRepository repository;
    private final SecretKeySpec hashKey;

    public ReceiptLinkTokenService(
        ReceiptLinkTokenRepository repository,
        @Value("${app.security.jwt.secret}") String signingSecret
    ) {
        byte[] key = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalArgumentException("Receipt-link token key must be at least 32 bytes.");
        }
        this.repository = repository;
        this.hashKey = new SecretKeySpec(key, "HmacSHA256");
    }

    @Transactional
    public ReceiptLinkTokenResponse createToken(Long userId, Long tenantId) {
        if (userId == null || userId <= 0 || tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        byte[] randomToken = new byte[32];
        SECURE_RANDOM.nextBytes(randomToken);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        String tokenHash = hash(rawToken);

        repository.save(new ReceiptLinkToken(
            userId,
            tenantId,
            tokenHash,
            LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        ));

        return new ReceiptLinkTokenResponse(rawToken, expiresAt.toString());
    }

    @Transactional
    public Optional<ReceiptRecipient> consumeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return repository.findUnconsumedByTokenHashForUpdate(hash(rawToken))
            .filter(token -> token.getExpiresAt().isAfter(now))
            .map(token -> {
                token.setConsumedAt(now);
                repository.save(token);
                return new ReceiptRecipient(token.getUserId(), token.getTenantId());
            });
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void deleteExpiredTokens() {
        repository.deleteExpiredBefore(
            LocalDateTime.now(ZoneOffset.UTC).minus(EXPIRED_TOKEN_RETENTION)
        );
    }

    private String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            byte[] digest = mac.doFinal((TOKEN_DOMAIN + token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect receipt-link token.", exception);
        }
    }
}
