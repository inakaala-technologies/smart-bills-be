package com.bhive.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private static final long TOKEN_LIFETIME_SECONDS = 15 * 60;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final SecretKeySpec signingKey;

    public AccessTokenService(ObjectMapper objectMapper, @Value("${app.security.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT signing secret must be at least 32 bytes.");
        }
        this.objectMapper = objectMapper;
        this.signingKey = new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    public String createAccessToken(Long userId, Collection<Long> tenantIds) {
        try {
            long issuedAt = System.currentTimeMillis() / 1000;
            byte[] header = objectMapper.writeValueAsBytes(java.util.Map.of("alg", "HS256", "typ", "JWT"));
            byte[] payload = objectMapper.writeValueAsBytes(java.util.Map.of(
                "sub", userId.toString(),
                "tenants", tenantIds,
                "iat", issuedAt,
                "exp", issuedAt + TOKEN_LIFETIME_SECONDS
            ));
            String unsignedToken = URL_ENCODER.encodeToString(header) + "." + URL_ENCODER.encodeToString(payload);
            return unsignedToken + "." + URL_ENCODER.encodeToString(sign(unsignedToken));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create access token.", exception);
        }
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return Optional.empty();
            }
            String unsignedToken = parts[0] + "." + parts[1];
            byte[] suppliedSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(unsignedToken), suppliedSignature)) {
                return Optional.empty();
            }

            JsonNode header = objectMapper.readTree(URL_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())) {
                return Optional.empty();
            }

            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            long userId = Long.parseLong(payload.path("sub").asText());
            long expiresAt = payload.path("exp").asLong();
            if (userId <= 0 || expiresAt <= System.currentTimeMillis() / 1000 || !payload.path("tenants").isArray()) {
                return Optional.empty();
            }

            List<Long> tenantIds = new ArrayList<>();
            for (JsonNode tenant : payload.path("tenants")) {
                long tenantId = tenant.asLong();
                if (tenantId > 0 && !tenantIds.contains(tenantId)) {
                    tenantIds.add(tenantId);
                }
            }
            if (tenantIds.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, List.copyOf(tenantIds)));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    public record AuthenticatedUser(Long userId, List<Long> tenantIds) {
    }
}