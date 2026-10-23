package com.orbit.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSessionId;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그아웃")
class LogoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    private static final String SESSION_ID = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b";

    @Mock
    private RevokedAccessTokenPort revokedTokens;

    @Mock
    private RefreshSessionRepository sessions;

    @Test
    @DisplayName("토큰을 남은 유효 시간 동안 폐기한다")
    void revokesTokenForRemainingLifetime() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), SESSION_ID));

        verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("이미 만료된 토큰은 남은 시간 0으로 넘긴다")
    void passesZeroForExpiredToken() {
        service().logout(new LogoutCommand(7L, "jti", NOW.minusSeconds(1), SESSION_ID));

        verify(revokedTokens).revoke("jti", Duration.ZERO);
    }

    @Test
    @DisplayName("토큰을 발급한 기기 세션을 그 계정의 것일 때만 지워 이 기기의 refresh token을 끝낸다")
    void endsDeviceSession() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), SESSION_ID));

        verify(sessions).deleteOwnedBy(new RefreshSessionId(SESSION_ID), new AccountId(7L));
    }

    @Test
    @DisplayName("refresh token 도입 전에 발급된 토큰이면 지울 기기 세션이 없다")
    void skipsSessionForTokenWithoutSession() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), null));

        verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
        verify(sessions, never()).deleteOwnedBy(any(), any());
    }

    private LogoutService service() {
        return new LogoutService(revokedTokens, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
