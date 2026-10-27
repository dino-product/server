package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림")
class NotificationTest {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-19T01:00:00Z");
    private static final Instant READ_AT = Instant.parse("2026-10-19T02:00:00Z");

    @Test
    @DisplayName("새 알림은 안 읽음이고 숨기지 않았다")
    void createsUnreadVisibleNotification() {
        Notification notification = newNotification();

        assertThat(notification.id()).isEmpty();
        assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.readAt()).isEmpty();
        assertThat(notification.isHidden()).isFalse();
        assertThat(notification.isReceivedBy(7L)).isTrue();
        assertThat(notification.isReceivedBy(8L)).isFalse();
    }

    @Test
    @DisplayName("읽으면 읽은 시각을 남기고 다시 읽어도 처음 읽은 시각을 그대로 둔다")
    void marksReadOnce() {
        Notification notification = newNotification();

        notification.markRead(READ_AT);
        notification.markRead(READ_AT.plusSeconds(60));

        assertThat(notification.status()).isEqualTo(NotificationStatus.READ);
        assertThat(notification.readAt()).contains(READ_AT);
    }

    @Test
    @DisplayName("주어진 시각이나 그 전에 생긴 알림만 숨기고 이미 숨긴 알림은 다시 숨기지 않는다")
    void hidesOnlyNotificationsNotifiedByGivenTime() {
        Notification notification = newNotification();

        assertThat(notification.hideIfNotifiedBy(NOTIFIED_AT.minusSeconds(1))).isFalse();
        assertThat(notification.isHidden()).isFalse();
        assertThat(notification.hideIfNotifiedBy(NOTIFIED_AT)).isTrue();
        assertThat(notification.isHidden()).isTrue();
        assertThat(notification.hideIfNotifiedBy(NOTIFIED_AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("작업과 무관한 알림은 작업 없이 만든다")
    void allowsNotificationWithoutWork() {
        Notification notification =
                Notification.create(7L, 1L, NotificationType.WORK_ACCEPTED, null, "문구", NOTIFIED_AT);

        assertThat(notification.workId()).isEmpty();
    }

    @Test
    @DisplayName("참여 요청 결과 알림은 관련 참여 요청을 두고 작업 없이 만든다")
    void createsParticipationRequestNotification() {
        Notification notification = Notification.createForParticipationRequest(
                7L, 1L, NotificationType.PARTICIPATION_REQUEST_REJECTED, 31L, "문구", NOTIFIED_AT);

        assertThat(notification.participationRequestId()).contains(31L);
        assertThat(notification.workId()).isEmpty();
        assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.isReceivedBy(7L)).isTrue();
    }

    @Test
    @DisplayName("작업 알림에는 관련 참여 요청이 없다")
    void workNotificationHasNoParticipationRequest() {
        assertThat(newNotification().participationRequestId()).isEmpty();
    }

    @Test
    @DisplayName("참여 요청 결과 알림과 작업 알림의 종류를 섞어 만들지 않고 참여 요청 식별자는 양수여야 한다")
    void rejectsMismatchedTypeAndParticipationRequest() {
        assertThatThrownBy(() -> Notification.create(
                        7L, 1L, NotificationType.PARTICIPATION_REQUEST_APPROVED, null, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.createForParticipationRequest(
                        7L, 1L, NotificationType.WORK_ASSIGNED, 31L, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.createForParticipationRequest(
                        7L, 1L, NotificationType.PARTICIPATION_REQUEST_APPROVED, null, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.createForParticipationRequest(
                        7L, 1L, NotificationType.PARTICIPATION_REQUEST_APPROVED, 0L, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장된 참여 요청 결과 알림을 관련 참여 요청과 함께 복원한다")
    void reconstitutesParticipationRequestNotification() {
        Notification notification = Notification.reconstitute(
                1L,
                7L,
                1L,
                NotificationType.PARTICIPATION_REQUEST_APPROVED,
                null,
                31L,
                "문구",
                NOTIFIED_AT,
                NotificationStatus.UNREAD,
                null,
                false);

        assertThat(notification.participationRequestId()).contains(31L);
        assertThat(notification.workId()).isEmpty();
    }

    @Test
    @DisplayName("식별자가 양수가 아니거나 문구가 비었거나 너무 길면 만들지 않는다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> Notification.create(0L, 1L, NotificationType.WORK_ASSIGNED, 10L, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(7L, null, NotificationType.WORK_ASSIGNED, 10L, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(7L, 1L, NotificationType.WORK_ASSIGNED, -1L, "문구", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(7L, 1L, NotificationType.WORK_ASSIGNED, 10L, " ", NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(
                        7L,
                        1L,
                        NotificationType.WORK_ASSIGNED,
                        10L,
                        "가".repeat(Notification.MAX_MESSAGE_LENGTH + 1),
                        NOTIFIED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("읽음 상태와 읽은 시각이 맞지 않는 저장값은 복원하지 않는다")
    void rejectsInconsistentReadState() {
        assertThatThrownBy(() -> Notification.reconstitute(
                        1L,
                        7L,
                        1L,
                        NotificationType.WORK_ASSIGNED,
                        10L,
                        null,
                        "문구",
                        NOTIFIED_AT,
                        NotificationStatus.READ,
                        null,
                        false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.reconstitute(
                        1L,
                        7L,
                        1L,
                        NotificationType.WORK_ASSIGNED,
                        10L,
                        null,
                        "문구",
                        NOTIFIED_AT,
                        NotificationStatus.UNREAD,
                        READ_AT,
                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Notification newNotification() {
        return Notification.create(7L, 1L, NotificationType.WORK_ASSIGNED, 10L, "문구", NOTIFIED_AT);
    }
}
