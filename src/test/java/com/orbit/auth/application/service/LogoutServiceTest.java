package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그아웃")
class LogoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    private static final String SECRET = "a".repeat(43);
    private static final String SESSION_ID = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b";

    @Mock
    private RevokedAccessTokenPort revokedTokens;

    @Mock
    private RefreshSessionRepository sessions;

    @Test
    @DisplayName("토큰을 남은 유효 시간 동안 폐기한다")
    void revokesTokenForRemainingLifetime() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), SESSION_ID, null));

        verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("이미 만료된 토큰은 남은 시간 0으로 넘긴다")
    void passesZeroForExpiredToken() {
        service().logout(new LogoutCommand(7L, "jti", NOW.minusSeconds(1), SESSION_ID, null));

        verify(revokedTokens).revoke("jti", Duration.ZERO);
    }

    @Test
    @DisplayName("토큰을 발급한 기기 세션을 그 계정의 것일 때만 지워 이 기기의 refresh token을 끝낸다")
    void endsDeviceSession() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), SESSION_ID, null));

        verify(sessions).deleteOwnedBy(new RefreshSessionId(SESSION_ID), new AccountId(7L));
    }

    @Test
    @DisplayName("refresh token 도입 전에 발급된 토큰이면 지울 기기 세션이 없다")
    void skipsSessionForTokenWithoutSession() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), null, null));

        verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
        verify(sessions, never()).deleteOwnedBy(any(), any());
    }

    @Test
    @DisplayName("기기 세션을 먼저 지우고 Access Token 폐기는 마지막에 기록한다")
    void deletesSessionBeforeRevokingToken() {
        service().logout(new LogoutCommand(7L, "jti", NOW.plus(Duration.ofMinutes(40)), SESSION_ID, null));

        InOrder order = inOrder(sessions, revokedTokens);
        order.verify(sessions).deleteOwnedBy(new RefreshSessionId(SESSION_ID), new AccountId(7L));
        order.verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("Access Token 없이 refresh token만 오면 그 비밀값과 맞는 세션을 지우고 폐기 목록은 건드리지 않는다")
    void endsSessionByRefreshToken() {
        service().logout(new LogoutCommand(null, null, null, null, SESSION_ID + "." + SECRET));

        verify(sessions).deleteByToken(new RefreshSessionId(SESSION_ID), RefreshTokenHash.of(SECRET));
        verify(revokedTokens, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("형식이 어긋난 refresh token은 무시한다")
    void ignoresMalformedRefreshToken() {
        service().logout(new LogoutCommand(null, null, null, null, "not-a-refresh-token"));

        verify(sessions, never()).deleteByToken(any(), any());
        verify(revokedTokens, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("Access Token도 refresh token도 없으면 저장소와 폐기 목록을 건드리지 않는다")
    void doesNothingWithoutCredentials() {
        service().logout(new LogoutCommand(null, null, null, null, null));

        verifyNoInteractions(sessions, revokedTokens);
    }

    @Test
    @DisplayName("명령의 문자열 표현에 refresh token을 남기지 않는다")
    void hidesRefreshTokenInCommand() {
        assertThat(new LogoutCommand(null, null, null, null, SESSION_ID + "." + SECRET).toString())
                .doesNotContain(SECRET);
    }

    private LogoutService service() {
        return new LogoutService(revokedTokens, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
