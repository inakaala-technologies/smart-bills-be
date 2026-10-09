package com.bhive.auth.service;

import com.bhive.auth.entity.RefreshToken;
import com.bhive.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void storesOnlyTokenHashAndRotatesWithinTheSameFamily() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        String rawToken = refreshTokenService.issueRefreshToken(42L);

        ArgumentCaptor<RefreshToken> issuedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(issuedToken.capture());
        assertNotEquals(rawToken, issuedToken.getValue().getTokenHash());
        assertEquals(42L, issuedToken.getValue().getUserId());

        RefreshToken currentToken = activeToken("current-hash", "family-1");
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(currentToken));

        var rotated = refreshTokenService.rotate("current-raw-token");

        assertEquals(42L, rotated.orElseThrow().userId());
        assertNotEquals("current-raw-token", rotated.get().rawToken());
        assertNotNull(currentToken.getRevokedAt());
        ArgumentCaptor<RefreshToken> savedTokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(3)).save(savedTokens.capture());
        assertEquals("family-1", savedTokens.getAllValues().get(2).getFamilyId());
    }

    @Test
    void replayingRevokedTokenRevokesAllActiveTokensInItsFamily() {
        RefreshToken revokedToken = activeToken("old-hash", "family-2");
        revokedToken.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        RefreshToken activeReplacement = activeToken("replacement-hash", "family-2");
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(revokedToken));
        when(refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull("family-2"))
            .thenReturn(List.of(activeReplacement));

        var result = refreshTokenService.rotate("replayed-token");

        assertFalse(result.isPresent());
        assertNotNull(activeReplacement.getRevokedAt());
        verify(refreshTokenRepository).saveAll(List.of(activeReplacement));
    }

    private RefreshToken activeToken(String tokenHash, String familyId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(42L);
        token.setTokenHash(tokenHash);
        token.setFamilyId(familyId);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
    }
}