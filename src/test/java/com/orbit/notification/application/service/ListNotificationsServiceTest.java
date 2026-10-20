package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.query.dto.CountUnreadNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.ListNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationInfo;
import com.orbit.notification.application.port.in.query.dto.NotificationPageInfo;
import com.orbit.notification.application.service.fake.InMemoryNotificationRepository;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;
import com.orbit.shared.error.BusinessException;

@DisplayName("알림 목록·안 읽은 수 조회")
class ListNotificationsServiceTest {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-19T01:00:00Z");
    private static final Long ACCOUNT_ID = 7L;

    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
    private final ListNotificationsService listService = new ListNotificationsService(notifications);
    private final CountUnreadNotificationsService countService = new CountUnreadNotificationsService(notifications);

    @Test
    @DisplayName("기본 쪽(0쪽, 20건)으로 본인 알림을 최신순으로 돌려주고 숨긴 알림과 다른 계정 알림은 뺀다")
    void listsOwnVisibleNotificationsLatestFirst() {
        Long older = save(ACCOUNT_ID, NOTIFIED_AT);
        Long newer = save(ACCOUNT_ID, NOTIFIED_AT.plusSeconds(60));
        hide(save(ACCOUNT_ID, NOTIFIED_AT.plusSeconds(120)));
        save(8L, NOTIFIED_AT.plusSeconds(180));

        NotificationPageInfo page = listService.list(new ListNotificationsQuery(ACCOUNT_ID, false, null, null));

        assertThat(page.notifications()).extracting(NotificationInfo::id).containsExactly(newer, older);
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(ListNotificationsService.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("안 읽은 알림만 골라 보고 안 읽은 수는 숨긴 알림을 세지 않는다")
    void filtersUnreadAndCountsVisibleUnread() {
        Long unread = save(ACCOUNT_ID, NOTIFIED_AT);
        Long read = save(ACCOUNT_ID, NOTIFIED_AT.plusSeconds(60));
        Notification readNotification = notifications.findById(read).orElseThrow();
        readNotification.markRead(NOTIFIED_AT.plusSeconds(90));
        notifications.save(readNotification);
        hide(save(ACCOUNT_ID, NOTIFIED_AT.plusSeconds(120)));

        NotificationPageInfo page = listService.list(new ListNotificationsQuery(ACCOUNT_ID, true, 0, 10));

        assertThat(page.notifications()).extracting(NotificationInfo::id).containsExactly(unread);
        assertThat(countService.count(new CountUnreadNotificationsQuery(ACCOUNT_ID)))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101", "101, 100"})
    @DisplayName("쪽 입력이 범위를 벗어나면 400으로 거부한다")
    void rejectsPageOutOfRange(int page, int size) {
        assertThatThrownBy(() -> listService.list(new ListNotificationsQuery(ACCOUNT_ID, false, page, size)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(NotificationErrorCode.INVALID_NOTIFICATION_INPUT));
    }

    @Test
    @DisplayName("끝을 넘은 쪽은 빈 목록과 전체 수를 돌려준다")
    void returnsEmptyPageBeyondEnd() {
        save(ACCOUNT_ID, NOTIFIED_AT);

        NotificationPageInfo page = listService.list(new ListNotificationsQuery(ACCOUNT_ID, false, 100, 100));

        assertThat(page.notifications()).isEmpty();
        assertThat(page.totalCount()).isEqualTo(1);
    }

    private Long save(Long accountId, Instant notifiedAt) {
        return notifications
                .save(Notification.create(accountId, 1L, NotificationType.WORK_ASSIGNED, 10L, "문구", notifiedAt))
                .id()
                .orElseThrow();
    }

    private void hide(Long id) {
        Notification notification = notifications.findById(id).orElseThrow();
        notification.hideIfNotifiedBy(notification.notifiedAt());
        notifications.save(notification);
    }
}
