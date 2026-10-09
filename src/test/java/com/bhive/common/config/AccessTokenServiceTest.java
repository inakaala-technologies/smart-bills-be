package com.bhive.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenServiceTest {

    @Test
    void verifiesSignedTokensAndRejectsTampering() {
        AccessTokenService service = new AccessTokenService(new ObjectMapper(), "test-signing-secret-with-at-least-32-bytes");
        String token = service.createAccessToken(42L, List.of(5L, 6L));
        String[] tokenParts = token.split("\\.");
        tokenParts[2] = (tokenParts[2].charAt(0) == 'A' ? "B" : "A") + tokenParts[2].substring(1);

        var authenticatedUser = service.verify(token);
        var tamperedUser = service.verify(String.join(".", tokenParts));

        assertTrue(authenticatedUser.isPresent());
        assertEquals(42L, authenticatedUser.get().userId());
        assertEquals(List.of(5L, 6L), authenticatedUser.get().tenantIds());
        assertTrue(tamperedUser.isEmpty());
    }
}