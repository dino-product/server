package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.IssuedAccessToken;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RefreshTokenSecretPort;
import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

@ExtendWith(MockitoExtension.class)
@DisplayName("기기 세션 시작과 토큰 발급")
class SessionTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T01:00:00Z");
    private static final Duration IDLE = Duration.ofDays(30);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final String SECRET = "a".repeat(43);

    @Mock
    private AccessTokenPort accessTokens;

    @Mock
    private RefreshSessionRepository sessions;

    @Mock
    private RefreshTokenSecretPort secrets;

    private SessionTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new SessionTokenIssuer(accessTokens, sessions, secrets, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 계정의 만료 세션을 지운 뒤 비밀값 해시로 새 세션을 저장하고 그 세션의 토큰을 발급한다")
    void startsSessionAndIssuesTokens() {
        when(secrets.newSecret()).thenReturn(SECRET);
        when(secrets.idleLifetime()).thenReturn(IDLE);
        when(accessTokens.issue(any(), any()))
                .thenAnswer(invocation -> new IssuedAccessToken(
                        "access-token",
                        new AccessToken(
                                "jti",
                                invocation.getArgument(0),
                                NOW,
                                NOW.plus(Duration.ofHours(1)),
                                invocation.getArgument(1))));

        IssuedSessionTokens issued = issuer.start(ACCOUNT_ID);

        ArgumentCaptor<RefreshSession> saved = ArgumentCaptor.forClass(RefreshSession.class);
        InOrder order = inOrder(sessions, accessTokens);
        order.verify(sessions).deleteExpiredByAccount(ACCOUNT_ID, NOW);
        order.verify(sessions).saveNew(saved.capture());
        RefreshSessionId sessionId = saved.getValue().id();
        order.verify(accessTokens).issue(ACCOUNT_ID, sessionId);
        assertThat(saved.getValue().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getValue().tokenHash()).isEqualTo(RefreshTokenHash.of(SECRET));
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(IDLE));
        assertThat(issued.accessToken()).isEqualTo("access-token");
        assertThat(issued.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(issued.refreshToken()).isEqualTo(sessionId.value() + "." + SECRET);
        assertThat(issued.refreshTokenExpiresAt()).isEqualTo(NOW.plus(IDLE));
    }

    @Test
    @DisplayName("세션마다 다른 식별자를 쓴다")
    void usesNewSessionIdPerLogin() {
        when(secrets.newSecret()).thenReturn(SECRET);
        when(secrets.idleLifetime()).thenReturn(IDLE);
        when(accessTokens.issue(any(), any()))
                .thenAnswer(invocation -> new IssuedAccessToken(
                        "access-token",
                        new AccessToken(
                                "jti",
                                invocation.getArgument(0),
                                NOW,
                                NOW.plus(Duration.ofHours(1)),
                                invocation.getArgument(1))));

        String first = issuer.start(ACCOUNT_ID).refreshToken();
        String second = issuer.start(ACCOUNT_ID).refreshToken();

        assertThat(first.substring(0, 36)).isNotEqualTo(second.substring(0, 36));
    }

    @Test
    @DisplayName("문자열 표현에 토큰을 남기지 않는다")
    void hidesTokensInToString() {
        IssuedSessionTokens issued = new IssuedSessionTokens("access-token", NOW, "session." + SECRET, NOW.plus(IDLE));

        assertThat(issued.toString()).doesNotContain("access-token").doesNotContain(SECRET);
    }
}
