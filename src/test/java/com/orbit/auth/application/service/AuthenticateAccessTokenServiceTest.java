package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.AccountId;

@ExtendWith(MockitoExtension.class)
@DisplayName("Access Token 인증")
class AuthenticateAccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final AccessToken TOKEN =
            new AccessToken("jti", new AccountId(7L), NOW, NOW.plus(Duration.ofHours(1)));
    private static final AuthenticateAccessTokenQuery QUERY = new AuthenticateAccessTokenQuery("access-token");

    @Mock
    private AccessTokenPort accessTokens;

    @Mock
    private RevokedAccessTokenPort revokedTokens;

    @InjectMocks
    private AuthenticateAccessTokenService service;

    @Test
    @DisplayName("유효하고 폐기되지 않은 토큰이면 계정과 식별자를 돌려준다")
    void authenticatesValidToken() {
        when(accessTokens.parse("access-token")).thenReturn(Optional.of(TOKEN));
        when(revokedTokens.isRevoked("jti")).thenReturn(false);

        assertThat(service.authenticate(QUERY)).contains(new AccessTokenInfo(7L, "jti", NOW.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("폐기된 토큰은 거부한다")
    void rejectsRevokedToken() {
        when(accessTokens.parse("access-token")).thenReturn(Optional.of(TOKEN));
        when(revokedTokens.isRevoked("jti")).thenReturn(true);

        assertThat(service.authenticate(QUERY)).isEmpty();
    }

    @Test
    @DisplayName("검증에 실패한 토큰은 폐기 목록을 보지 않고 거부한다")
    void rejectsInvalidTokenWithoutRevocationLookup() {
        when(accessTokens.parse("access-token")).thenReturn(Optional.empty());

        assertThat(service.authenticate(QUERY)).isEmpty();
        verify(revokedTokens, never()).isRevoked(any());
    }
}
