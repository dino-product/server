package com.orbit.auth.application.service;

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
import com.orbit.auth.application.port.out.RevokedAccessTokenPort;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그아웃")
class LogoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    @Mock
    private RevokedAccessTokenPort revokedTokens;

    @Test
    @DisplayName("토큰을 남은 유효 시간 동안 폐기한다")
    void revokesTokenForRemainingLifetime() {
        new LogoutService(revokedTokens, Clock.fixed(NOW, ZoneOffset.UTC))
                .logout(new LogoutCommand("jti", NOW.plus(Duration.ofMinutes(40))));

        verify(revokedTokens).revoke("jti", Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("이미 만료된 토큰은 남은 시간 0으로 넘긴다")
    void passesZeroForExpiredToken() {
        new LogoutService(revokedTokens, Clock.fixed(NOW, ZoneOffset.UTC))
                .logout(new LogoutCommand("jti", NOW.minusSeconds(1)));

        verify(revokedTokens).revoke("jti", Duration.ZERO);
    }
}
