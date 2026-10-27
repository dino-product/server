package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.NotificationRead;
import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.dto.ReadNotificationCommand;
import com.orbit.notification.application.service.fake.InMemoryNotificationRepository;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@DisplayName("알림 읽음 처리")
class ReadNotificationServiceTest {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-19T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T02:00:00Z");
    private static final Long ACCOUNT_ID = 7L;

    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
    private final List<Object> published = new ArrayList<>();

    private ReadNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ReadNotificationService(notifications, published::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("본인이 받은 알림을 지금 시각에 읽음으로 바꾸고 읽음을 발행한다")
    void marksOwnNotificationRead() {
        Long id = save(ACCOUNT_ID);

        service.read(new ReadNotificationCommand(ACCOUNT_ID, id));

        assertThat(notifications.findById(id)).hasValueSatisfying(found -> {
            assertThat(found.isRead()).isTrue();
            assertThat(found.readAt()).contains(NOW);
        });
        assertThat(published).containsExactly(new NotificationRead(ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("이미 읽은 알림은 그대로 성공하고 처음 읽은 시각을 바꾸지 않으며 다시 발행하지 않는다")
    void keepsFirstReadTime() {
        Long id = save(ACCOUNT_ID);
        Notification read = notifications.findById(id).orElseThrow();
        read.markRead(NOTIFIED_AT.plusSeconds(10));
        notifications.save(read);

        service.read(new ReadNotificationCommand(ACCOUNT_ID, id));

        assertThat(notifications.findById(id).orElseThrow().readAt()).contains(NOTIFIED_AT.plusSeconds(10));
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("다른 계정이 받은 알림은 403으로 거부하고 읽음으로 바꾸지 않는다")
    void deniesNotificationOfOtherAccount() {
        Long id = save(8L);

        assertError(
                () -> service.read(new ReadNotificationCommand(ACCOUNT_ID, id)),
                NotificationErrorCode.NOT_NOTIFICATION_RECIPIENT);
        assertThat(notifications.findById(id).orElseThrow().isRead()).isFalse();
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("없는 알림과 숨긴 본인 알림은 404로 거부한다")
    void rejectsMissingOrHiddenNotification() {
        Long hidden = save(ACCOUNT_ID);
        Notification notification = notifications.findById(hidden).orElseThrow();
        notification.hideIfNotifiedBy(NOTIFIED_AT);
        notifications.save(notification);

        assertError(
                () -> service.read(new ReadNotificationCommand(ACCOUNT_ID, 999L)),
                NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertError(
                () -> service.read(new ReadNotificationCommand(ACCOUNT_ID, hidden)),
                NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(published).isEmpty();
    }

    private Long save(Long accountId) {
        return notifications
                .save(Notification.create(accountId, 1L, NotificationType.WORK_ASSIGNED, 10L, "문구", NOTIFIED_AT))
                .id()
                .orElseThrow();
    }

    private static void assertError(Runnable call, BaseCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
    }
}
