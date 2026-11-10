package com.bhive.receipt.service;

import com.bhive.receipt.dto.ReceiptLinkTokenResponse;
import com.bhive.receipt.entity.ReceiptLinkToken;
import com.bhive.receipt.repository.ReceiptLinkTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptLinkTokenServiceTest {

    @Mock
    private ReceiptLinkTokenRepository repository;

    @Test
    void createTokenReturnsRawTokenAndPersistsOnlyItsHash() {
        ReceiptLinkTokenService service = new ReceiptLinkTokenService(repository, "a".repeat(32));
        when(repository.save(any(ReceiptLinkToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptLinkTokenResponse response = service.createToken(42L, 7L);
        ArgumentCaptor<ReceiptLinkToken> tokenCaptor = ArgumentCaptor.forClass(ReceiptLinkToken.class);
        verify(repository).save(tokenCaptor.capture());
        ReceiptLinkToken persistedToken = tokenCaptor.getValue();

        assertTrue(response.token().matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(response.token(), persistedToken.getTokenHash());
        assertEquals(42L, persistedToken.getUserId());
        assertEquals(7L, persistedToken.getTenantId());
        assertTrue(persistedToken.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
        assertTrue(persistedToken.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(6)));
        assertTrue(response.expiresAt().endsWith("Z"));
    }

    @Test
    void consumeTokenIsOneUseAndReturnsTheLinkedUser() {
        ReceiptLinkTokenService service = new ReceiptLinkTokenService(repository, "b".repeat(32));
        ReceiptLinkToken issuedToken = new ReceiptLinkToken(
            42L,
            "unused-in-this-test",
            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)
        );
        when(repository.findUnconsumedByTokenHashForUpdate(any())).thenReturn(Optional.of(issuedToken));
        when(repository.save(any(ReceiptLinkToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ReceiptRecipient> recipient = service.consumeToken("valid-token");

        assertEquals(Optional.of(new ReceiptRecipient(42L, null)), recipient);
        assertTrue(issuedToken.getConsumedAt() != null);
    }

    @Test
    void consumeExpiredTokenReturnsEmpty() {
        ReceiptLinkTokenService service = new ReceiptLinkTokenService(repository, "c".repeat(32));
        ReceiptLinkToken expiredToken = new ReceiptLinkToken(
            42L,
            "unused-in-this-test",
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        );
        when(repository.findUnconsumedByTokenHashForUpdate(any())).thenReturn(Optional.of(expiredToken));

        assertFalse(service.consumeToken("expired-token").isPresent());
    }
}
