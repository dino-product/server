package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("제공자 연결 해제 대기")
class ProviderUnlinkTest {

    private static final AccountId ACCOUNT_ID = new AccountId(10L);
    private static final ExternalIdentity KAKAO = new ExternalIdentity(OAuthProvider.KAKAO, "4012345678");
    private static final ExternalIdentity APPLE = new ExternalIdentity(OAuthProvider.APPLE, "001234.abcdef.0123");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-11T03:00:00Z");

    @Test
    @DisplayName("카카오는 연결 끊기에 필요한 회원번호를 남기고, 커밋 직후 시도가 실패할 때를 대비해 1분 뒤 재시도를 잡아 둔다")
    void requestsKakaoUnlinkWithSubject() {
        ProviderUnlink unlink = ProviderUnlink.request(ACCOUNT_ID, KAKAO, REQUESTED_AT);

        assertThat(unlink.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(unlink.subject()).isEqualTo(KAKAO.subject());
        assertThat(unlink.failedAttempts()).isZero();
        assertThat(unlink.nextAttemptAt()).isEqualTo(REQUESTED_AT.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("Apple은 계정에 보관한 refresh token으로 철회하므로 회원번호를 남기지 않는다")
    void requestsAppleUnlinkWithoutSubject() {
        ProviderUnlink unlink = ProviderUnlink.request(ACCOUNT_ID, APPLE, REQUESTED_AT);

        assertThat(unlink.provider()).isEqualTo(OAuthProvider.APPLE);
        assertThat(unlink.subject()).isNull();
    }

    @Test
    @DisplayName("실패할 때마다 1분·5분·30분·2시간·12시간 뒤, 그다음부터는 하루 뒤에 다시 시도한다")
    void backsOffAfterEachFailure() {
        ProviderUnlink unlink = ProviderUnlink.request(ACCOUNT_ID, KAKAO, REQUESTED_AT);
        Instant failedAt = REQUESTED_AT;
        List<Duration> delays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            unlink = unlink.afterFailure(failedAt).orElseThrow();
            delays.add(Duration.between(failedAt, unlink.nextAttemptAt()));
            failedAt = unlink.nextAttemptAt();
        }

        assertThat(delays)
                .containsExactly(
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(30),
                        Duration.ofHours(2),
                        Duration.ofHours(12),
                        Duration.ofDays(1),
                        Duration.ofDays(1));
        assertThat(unlink.failedAttempts()).isEqualTo(7);
        assertThat(unlink.subject()).isEqualTo(KAKAO.subject());
        assertThat(unlink.requestedAt()).isEqualTo(REQUESTED_AT);
    }

    @Test
    @DisplayName("다음 시도가 요청 7일 뒤를 넘으면 포기한다")
    void givesUpAfterSevenDays() {
        ProviderUnlink unlink = new ProviderUnlink(
                ACCOUNT_ID,
                OAuthProvider.KAKAO,
                KAKAO.subject(),
                REQUESTED_AT,
                9,
                REQUESTED_AT.plus(Duration.ofDays(6)));

        Optional<ProviderUnlink> lastRetry = unlink.afterFailure(REQUESTED_AT.plus(Duration.ofDays(6)));
        Optional<ProviderUnlink> beyond =
                unlink.afterFailure(REQUESTED_AT.plus(Duration.ofDays(6)).plusSeconds(1));

        assertThat(lastRetry).map(ProviderUnlink::nextAttemptAt).contains(REQUESTED_AT.plus(Duration.ofDays(7)));
        assertThat(beyond).isEmpty();
    }

    @Test
    @DisplayName("카카오는 회원번호가 있어야 하고 Apple은 회원번호를 가질 수 없다")
    void validatesSubjectPerProvider() {
        assertThatThrownBy(
                        () -> new ProviderUnlink(ACCOUNT_ID, OAuthProvider.KAKAO, null, REQUESTED_AT, 0, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kakao unlink requires subject");
        assertThatThrownBy(() -> new ProviderUnlink(
                        ACCOUNT_ID, OAuthProvider.APPLE, APPLE.subject(), REQUESTED_AT, 0, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apple unlink must not keep subject");
        assertThatThrownBy(() -> new ProviderUnlink(
                        ACCOUNT_ID, OAuthProvider.KAKAO, KAKAO.subject(), REQUESTED_AT, -1, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAttempts must not be negative");
    }

    @Test
    @DisplayName("문자열 표현에는 회원번호를 넣지 않는다")
    void hidesSubjectInText() {
        assertThat(ProviderUnlink.request(ACCOUNT_ID, KAKAO, REQUESTED_AT).toString())
                .doesNotContain(KAKAO.subject())
                .contains("KAKAO");
    }
}
