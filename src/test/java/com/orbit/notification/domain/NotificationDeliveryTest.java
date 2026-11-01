package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림 발신 대기")
class NotificationDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant LEASE_UNTIL = NOW.plus(Duration.ofMinutes(5));
    private static final Long NOTIFICATION_ID = 10L;
    private static final Long RECIPIENT = 20L;

    @Test
    @DisplayName("새 푸시 발신은 대기 상태로 바로 보낼 수 있게 만든다")
    void createsPendingPushDueNow() {
        NotificationDelivery delivery = NotificationDelivery.pendingPush(NOTIFICATION_ID, RECIPIENT, NOW);

        assertThat(delivery.id()).isEmpty();
        assertThat(delivery.channel()).isEqualTo(DeliveryChannel.PUSH);
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.attempts()).isZero();
        assertThat(delivery.nextAttemptAt()).isEqualTo(NOW);
        assertThat(delivery.isDue(NOW)).isTrue();
        assertThat(delivery.isDue(NOW.minusMillis(1))).isFalse();
    }

    @Test
    @DisplayName("집으면 발신 중으로 바꾸고 시도 횟수를 늘리며 점유 만료 시각을 다음 시도 시각으로 둔다")
    void claimMarksInFlightWithLease() {
        NotificationDelivery claimed = pending().claim(NOW, LEASE_UNTIL);

        assertThat(claimed.status()).isEqualTo(DeliveryStatus.IN_FLIGHT);
        assertThat(claimed.attempts()).isEqualTo(1);
        assertThat(claimed.nextAttemptAt()).isEqualTo(LEASE_UNTIL);
        assertThat(claimed.isDue(LEASE_UNTIL.minusMillis(1)))
                .as("점유 중에는 다시 집지 않는다")
                .isFalse();
        assertThat(claimed.isDue(LEASE_UNTIL)).as("점유가 만료되면 다시 집을 수 있다").isTrue();
        assertThat(claimed.claim(LEASE_UNTIL, LEASE_UNTIL.plus(Duration.ofMinutes(5)))
                        .attempts())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("보낼 때가 아니거나 끝난 발신은 집을 수 없다")
    void rejectsClaimWhenNotDue() {
        assertThatThrownBy(() -> pending().claim(NOW.minusSeconds(1), LEASE_UNTIL))
                .isInstanceOf(IllegalStateException.class);
        NotificationDelivery sent = pending().claim(NOW, LEASE_UNTIL).markSent("provider-1", NOW);
        assertThatThrownBy(() -> sent.claim(LEASE_UNTIL, LEASE_UNTIL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("보내면 발신 완료로 바꾸고 제공자 메시지 식별자와 보낸 시각을 남긴다")
    void markSentRecordsProviderMessage() {
        NotificationDelivery sent = pending().claim(NOW, LEASE_UNTIL).markSent("provider-1", NOW);

        assertThat(sent.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(sent.providerMessageId()).contains("provider-1");
        assertThat(sent.sentAt()).contains(NOW);
        assertThat(sent.isDue(LEASE_UNTIL.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    @DisplayName("다시 보낼 실패는 대기로 되돌리고 오류와 다음 시도 시각을 남긴다")
    void retryAtReturnsToPending() {
        Instant retryAt = NOW.plusSeconds(30);
        NotificationDelivery retried = pending().claim(NOW, LEASE_UNTIL).retryAt("일시 오류", retryAt);

        assertThat(retried.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(retried.attempts()).isEqualTo(1);
        assertThat(retried.nextAttemptAt()).isEqualTo(retryAt);
        assertThat(retried.lastError()).contains("일시 오류");
        assertThat(retried.isDue(retryAt)).isTrue();
    }

    @Test
    @DisplayName("기기가 없으면 건너뜀, 다시 보내도 성공할 수 없으면 실패로 끝내고 다시 집지 않는다")
    void skipAndFailAreFinal() {
        NotificationDelivery skipped = pending().claim(NOW, LEASE_UNTIL).skip("등록된 기기 없음");
        NotificationDelivery failed = pending().claim(NOW, LEASE_UNTIL).fail("요청 형식 오류");

        assertThat(skipped.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(skipped.lastError()).contains("등록된 기기 없음");
        assertThat(failed.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.lastError()).contains("요청 형식 오류");
        Instant muchLater = LEASE_UNTIL.plus(Duration.ofDays(1));
        assertThat(skipped.isDue(muchLater)).isFalse();
        assertThat(failed.isDue(muchLater)).isFalse();
    }

    @Test
    @DisplayName("결과는 발신 중인 발신에만 기록한다")
    void recordsResultOnlyWhileInFlight() {
        NotificationDelivery pending = pending();

        assertThatThrownBy(() -> pending.markSent("provider-1", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.retryAt("오류", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.skip("기기 없음")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.fail("오류")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("오류 내용은 저장 길이에 맞춰 자른다")
    void truncatesLongError() {
        String longError = "e".repeat(NotificationDelivery.MAX_ERROR_LENGTH + 10);

        NotificationDelivery retried = pending().claim(NOW, LEASE_UNTIL).retryAt(longError, NOW);

        assertThat(retried.lastError())
                .hasValueSatisfying(error -> assertThat(error).hasSize(NotificationDelivery.MAX_ERROR_LENGTH));
    }

    @Test
    @DisplayName("수신 계정·알림·시각이 없거나 잘못되면 만들지 않는다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> NotificationDelivery.pendingPush(null, RECIPIENT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationDelivery.pendingPush(NOTIFICATION_ID, 0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationDelivery.pendingPush(NOTIFICATION_ID, RECIPIENT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationDelivery.reconstitute(
                        new NotificationDeliveryId(1L),
                        NOTIFICATION_ID,
                        RECIPIENT,
                        DeliveryChannel.PUSH,
                        DeliveryStatus.SENT,
                        1,
                        NOW,
                        null,
                        "provider-1",
                        null))
                .as("보낸 발신은 보낸 시각이 있어야 한다")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationDelivery.reconstitute(
                        new NotificationDeliveryId(1L),
                        NOTIFICATION_ID,
                        RECIPIENT,
                        DeliveryChannel.PUSH,
                        DeliveryStatus.PENDING,
                        -1,
                        NOW,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NotificationDelivery pending() {
        return NotificationDelivery.reconstitute(
                new NotificationDeliveryId(1L),
                NOTIFICATION_ID,
                RECIPIENT,
                DeliveryChannel.PUSH,
                DeliveryStatus.PENDING,
                0,
                NOW,
                null,
                null,
                null);
    }
}
