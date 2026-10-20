package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.command.dto.RefreshTokensCommand;
import com.orbit.auth.application.port.in.command.dto.RefreshedTokensInfo;
import com.orbit.auth.application.port.out.RefreshSessionRepository;
import com.orbit.auth.application.port.out.RefreshTokenSecretPort;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("토큰 재발급")
class RefreshTokensServiceTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-17T01:00:00Z");
    private static final Instant NOW = STARTED_AT.plus(Duration.ofHours(2));
    private static final Duration IDLE = Duration.ofDays(30);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final RefreshSessionId SESSION_ID = new RefreshSessionId("7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b");
    private static final String FIRST = "a".repeat(43);
    private static final String SECOND = "b".repeat(43);
    private static final String THIRD = "c".repeat(43);

    @Mock
    private RefreshSessionRepository sessions;

    @Mock
    private RefreshTokenSecretPort secrets;

    @Mock
    private SessionTokenIssuer tokens;

    private RefreshTokensService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokensService(sessions, secrets, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("현재 refresh token이면 다음 토큰으로 회전해 저장하고 새 토큰 쌍을 발급한다")
    void rotatesCurrentToken() {
        RefreshSession session = startedSession();
        givenSession(session);
        when(secrets.next(SESSION_ID, FIRST)).thenReturn(SECOND);
        when(secrets.idleLifetime()).thenReturn(IDLE);
        when(tokens.issue(any(), any())).thenReturn(issued(SECOND));

        RefreshedTokensInfo info = service.refresh(command(FIRST));

        verify(sessions).update(session);
        assertThat(session.tokenHash()).isEqualTo(RefreshTokenHash.of(SECOND));
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(IDLE));
        ArgumentCaptor<RefreshTokenValue> next = ArgumentCaptor.forClass(RefreshTokenValue.class);
        verify(tokens).issue(any(), next.capture());
        assertThat(next.getValue()).isEqualTo(new RefreshTokenValue(SESSION_ID, SECOND));
        assertThat(info.accessToken()).isEqualTo("access-token");
        assertThat(info.refreshToken()).isEqualTo(token(SECOND));
        assertThat(info.refreshTokenExpiresAt()).isEqualTo(NOW.plus(IDLE));
    }

    @Test
    @DisplayName("회전 직후 직전 토큰이 다시 오면 저장하지 않고 같은 다음 토큰을 다시 발급한다")
    void replaysJustRotatedToken() {
        RefreshSession session = rotatedSession(NOW.minusSeconds(5));
        givenSession(session);
        when(secrets.next(SESSION_ID, FIRST)).thenReturn(SECOND);
        when(secrets.idleLifetime()).thenReturn(IDLE);
        when(tokens.issue(any(), any())).thenReturn(issued(SECOND));

        RefreshedTokensInfo info = service.refresh(command(FIRST));

        verify(sessions, never()).update(any());
        verify(sessions, never()).delete(any());
        assertThat(info.refreshToken()).isEqualTo(token(SECOND));
    }

    @Test
    @DisplayName("유예가 지난 뒤 이미 회전된 토큰이 오면 세션을 철회하고 거절한다")
    void revokesSessionOnReuse() {
        RefreshSession session = rotatedSession(NOW.minus(RefreshSession.REPLAY_GRACE));
        givenSession(session);
        when(secrets.next(SESSION_ID, FIRST)).thenReturn(SECOND);
        when(secrets.idleLifetime()).thenReturn(IDLE);

        assertRejected(() -> service.refresh(command(FIRST)));

        verify(sessions).delete(SESSION_ID);
        verify(tokens, never()).issue(any(), any());
    }

    @Test
    @DisplayName("유예 안이라도 다시 만든 다음 토큰이 현재와 다르면 세션은 두고 거절한다")
    void rejectsStaleReplayWithoutRevoking() {
        RefreshSession session = rotatedSession(NOW.minusSeconds(5));
        givenSession(session);
        when(secrets.next(SESSION_ID, FIRST)).thenReturn(THIRD);
        when(secrets.idleLifetime()).thenReturn(IDLE);

        assertRejected(() -> service.refresh(command(FIRST)));

        verify(sessions, never()).delete(any());
        verify(sessions, never()).update(any());
    }

    @Test
    @DisplayName("만료된 세션이면 지우고 거절한다")
    void deletesExpiredSession() {
        RefreshSession session = RefreshSession.start(
                SESSION_ID, ACCOUNT_ID, RefreshTokenHash.of(FIRST), STARTED_AT, Duration.ofHours(1));
        givenSession(session);
        when(secrets.next(SESSION_ID, FIRST)).thenReturn(SECOND);
        when(secrets.idleLifetime()).thenReturn(IDLE);

        assertRejected(() -> service.refresh(command(FIRST)));

        verify(sessions).delete(SESSION_ID);
        verify(tokens, never()).issue(any(), any());
    }

    @Test
    @DisplayName("저장된 세션이 없으면 거절한다")
    void rejectsUnknownSession() {
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertRejected(() -> service.refresh(command(FIRST)));

        verify(sessions, never()).delete(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-refresh-token", "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.short"})
    @DisplayName("형식이 어긋난 refresh token은 저장소를 보지 않고 거절한다")
    void rejectsMalformedToken(String value) {
        assertRejected(() -> service.refresh(new RefreshTokensCommand(value)));

        verify(sessions, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("명령의 문자열 표현에 refresh token을 남기지 않는다")
    void hidesTokenInCommand() {
        assertThat(command(FIRST).toString()).doesNotContain(FIRST);
        assertThat(new RefreshedTokensInfo("access-token", NOW, token(SECOND), NOW.plus(IDLE)).toString())
                .doesNotContain("access-token")
                .doesNotContain(SECOND);
    }

    private void givenSession(RefreshSession session) {
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private static RefreshSession startedSession() {
        return RefreshSession.start(SESSION_ID, ACCOUNT_ID, RefreshTokenHash.of(FIRST), STARTED_AT, IDLE);
    }

    private static RefreshSession rotatedSession(Instant rotatedAt) {
        return RefreshSession.reconstitute(
                SESSION_ID,
                ACCOUNT_ID,
                RefreshTokenHash.of(SECOND),
                RefreshTokenHash.of(FIRST),
                rotatedAt,
                STARTED_AT,
                rotatedAt.plus(IDLE));
    }

    private static RefreshTokensCommand command(String secret) {
        return new RefreshTokensCommand(token(secret));
    }

    private static String token(String secret) {
        return SESSION_ID.value() + "." + secret;
    }

    private static IssuedSessionTokens issued(String secret) {
        return new IssuedSessionTokens("access-token", NOW.plus(Duration.ofHours(1)), token(secret), NOW.plus(IDLE));
    }

    private static void assertRejected(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
