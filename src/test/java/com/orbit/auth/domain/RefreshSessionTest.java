package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("기기 세션")
class RefreshSessionTest {

    private static final RefreshSessionId SESSION_ID = new RefreshSessionId("7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b");
    private static final AccountId ACCOUNT_ID = new AccountId(1L);
    private static final Duration IDLE = Duration.ofDays(30);
    private static final Instant STARTED_AT = Instant.parse("2026-10-17T01:00:00Z");
    private static final RefreshTokenHash FIRST = RefreshTokenHash.of("first-secret");
    private static final RefreshTokenHash SECOND = RefreshTokenHash.of("second-secret");
    private static final RefreshTokenHash THIRD = RefreshTokenHash.of("third-secret");

    @Nested
    @DisplayName("시작")
    class Start {

        @Test
        @DisplayName("첫 토큰 해시로 시작하고 미사용 수명만큼 뒤에 만료한다")
        void startsWithFirstToken() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

            assertThat(session.id()).isEqualTo(SESSION_ID);
            assertThat(session.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(session.tokenHash()).isEqualTo(FIRST);
            assertThat(session.previousTokenHash()).isEmpty();
            assertThat(session.rotatedAt()).isEmpty();
            assertThat(session.startedAt()).isEqualTo(STARTED_AT);
            assertThat(session.expiresAt()).isEqualTo(STARTED_AT.plus(IDLE));
        }

        @Test
        @DisplayName("미사용 수명이 0이거나 음수면 거부한다")
        void rejectsNonPositiveIdleLifetime() {
            assertThatThrownBy(() -> RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idleLifetime must be positive");
            assertThatThrownBy(() -> RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE.negated()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idleLifetime must be positive");
        }

        @Test
        @DisplayName("식별자·계정·해시·시각이 없으면 거부한다")
        void rejectsMissingValues() {
            assertThatThrownBy(() -> RefreshSession.start(null, ACCOUNT_ID, FIRST, STARTED_AT, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("id must not be null");
            assertThatThrownBy(() -> RefreshSession.start(SESSION_ID, null, FIRST, STARTED_AT, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("accountId must not be null");
            assertThatThrownBy(() -> RefreshSession.start(SESSION_ID, ACCOUNT_ID, null, STARTED_AT, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("tokenHash must not be null");
            assertThatThrownBy(() -> RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, null, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("startedAt must not be null");
        }
    }

    @Nested
    @DisplayName("재발급")
    class Refresh {

        @Test
        @DisplayName("현재 토큰이면 다음 토큰으로 회전하고 그 시각부터 만료를 연장한다")
        void rotatesCurrentToken() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);
            Instant now = STARTED_AT.plus(Duration.ofDays(29));

            assertThat(session.refresh(FIRST, SECOND, now, IDLE)).isEqualTo(RefreshOutcome.ROTATED);

            assertThat(session.tokenHash()).isEqualTo(SECOND);
            assertThat(session.previousTokenHash()).contains(FIRST);
            assertThat(session.rotatedAt()).contains(now);
            assertThat(session.expiresAt()).isEqualTo(now.plus(IDLE));
        }

        @Test
        @DisplayName("회전 뒤 유예 안에 직전 토큰이 다시 오고 다음 토큰이 현재와 같으면 상태를 바꾸지 않고 다시 보낸다")
        void replaysPreviousTokenWithinGrace() {
            RefreshSession session = rotatedSession();
            Instant replayAt = rotatedAt().plus(RefreshSession.REPLAY_GRACE).minusMillis(1);

            assertThat(session.refresh(FIRST, SECOND, replayAt, IDLE)).isEqualTo(RefreshOutcome.REPLAYED);

            assertThat(session.tokenHash()).isEqualTo(SECOND);
            assertThat(session.previousTokenHash()).contains(FIRST);
            assertThat(session.rotatedAt()).contains(rotatedAt());
            assertThat(session.expiresAt()).isEqualTo(rotatedAt().plus(IDLE));
        }

        @Test
        @DisplayName("유예 안의 직전 토큰이라도 다시 만든 다음 토큰이 현재와 다르면 세션을 두고 거절한다")
        void rejectsReplayWhoseNextTokenDiffers() {
            RefreshSession session = rotatedSession();

            assertThat(session.refresh(FIRST, THIRD, rotatedAt().plusSeconds(1), IDLE))
                    .isEqualTo(RefreshOutcome.STALE);

            assertThat(session.tokenHash()).isEqualTo(SECOND);
            assertThat(session.previousTokenHash()).contains(FIRST);
        }

        @Test
        @DisplayName("유예가 끝난 시각부터 직전 토큰은 재사용이다")
        void treatsPreviousTokenAfterGraceAsReuse() {
            RefreshSession session = rotatedSession();

            assertThat(session.refresh(FIRST, SECOND, rotatedAt().plus(RefreshSession.REPLAY_GRACE), IDLE))
                    .isEqualTo(RefreshOutcome.REUSED);
        }

        @Test
        @DisplayName("현재·직전 어느 토큰과도 맞지 않으면 재사용이다")
        void treatsUnknownTokenAsReuse() {
            RefreshSession session = rotatedSession();

            assertThat(session.refresh(
                            THIRD, RefreshTokenHash.of("next"), rotatedAt().plusSeconds(1), IDLE))
                    .isEqualTo(RefreshOutcome.REUSED);
        }

        @Test
        @DisplayName("한 번도 회전하지 않은 세션에 다른 토큰이 오면 재사용이다")
        void treatsMismatchBeforeFirstRotationAsReuse() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

            assertThat(session.refresh(SECOND, THIRD, STARTED_AT.plusSeconds(1), IDLE))
                    .isEqualTo(RefreshOutcome.REUSED);
        }

        @Test
        @DisplayName("만료 시각부터는 현재 토큰도 만료로 거절하고 상태를 바꾸지 않는다")
        void rejectsExpiredSession() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

            assertThat(session.refresh(FIRST, SECOND, STARTED_AT.plus(IDLE), IDLE))
                    .isEqualTo(RefreshOutcome.EXPIRED);

            assertThat(session.tokenHash()).isEqualTo(FIRST);
            assertThat(session.previousTokenHash()).isEmpty();
        }

        @Test
        @DisplayName("만료 직전의 현재 토큰은 회전한다")
        void rotatesJustBeforeExpiry() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

            assertThat(session.refresh(FIRST, SECOND, STARTED_AT.plus(IDLE).minusMillis(1), IDLE))
                    .isEqualTo(RefreshOutcome.ROTATED);
        }

        @Test
        @DisplayName("값이 없으면 거부한다")
        void rejectsMissingValues() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

            assertThatThrownBy(() -> session.refresh(null, SECOND, STARTED_AT, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("presented must not be null");
            assertThatThrownBy(() -> session.refresh(FIRST, null, STARTED_AT, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("next must not be null");
            assertThatThrownBy(() -> session.refresh(FIRST, SECOND, null, IDLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("now must not be null");
            assertThatThrownBy(() -> session.refresh(FIRST, SECOND, STARTED_AT, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idleLifetime must be positive");
        }

        private static Instant rotatedAt() {
            return STARTED_AT.plus(Duration.ofHours(1));
        }

        private static RefreshSession rotatedSession() {
            RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);
            session.refresh(FIRST, SECOND, rotatedAt(), IDLE);
            return session;
        }
    }

    @Nested
    @DisplayName("재구성")
    class Reconstitute {

        @Test
        @DisplayName("저장된 회전 상태를 그대로 되살린다")
        void reconstitutesRotatedSession() {
            Instant rotatedAt = STARTED_AT.plusSeconds(60);

            RefreshSession session = RefreshSession.reconstitute(
                    SESSION_ID, ACCOUNT_ID, SECOND, FIRST, rotatedAt, STARTED_AT, rotatedAt.plus(IDLE));

            assertThat(session.tokenHash()).isEqualTo(SECOND);
            assertThat(session.previousTokenHash()).contains(FIRST);
            assertThat(session.rotatedAt()).contains(rotatedAt);
            assertThat(session.expiresAt()).isEqualTo(rotatedAt.plus(IDLE));
        }

        @Test
        @DisplayName("직전 토큰과 회전 시각은 함께 있거나 함께 없어야 한다")
        void rejectsPreviousTokenWithoutRotatedAt() {
            assertThatThrownBy(() -> RefreshSession.reconstitute(
                            SESSION_ID, ACCOUNT_ID, SECOND, FIRST, null, STARTED_AT, STARTED_AT.plus(IDLE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("previousTokenHash and rotatedAt must be both present or both absent");
            assertThatThrownBy(() -> RefreshSession.reconstitute(
                            SESSION_ID, ACCOUNT_ID, SECOND, null, STARTED_AT, STARTED_AT, STARTED_AT.plus(IDLE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("previousTokenHash and rotatedAt must be both present or both absent");
        }

        @Test
        @DisplayName("만료 시각이 시작 시각보다 늦어야 한다")
        void rejectsExpiryNotAfterStart() {
            assertThatThrownBy(() -> RefreshSession.reconstitute(
                            SESSION_ID, ACCOUNT_ID, FIRST, null, null, STARTED_AT, STARTED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expiresAt must be after startedAt");
        }
    }

    @Test
    @DisplayName("문자열 표현에 토큰 해시를 남기지 않는다")
    void hidesTokenHashesInToString() {
        RefreshSession session = RefreshSession.start(SESSION_ID, ACCOUNT_ID, FIRST, STARTED_AT, IDLE);

        assertThat(session.toString()).doesNotContain(FIRST.value()).contains(SESSION_ID.value());
    }
}
