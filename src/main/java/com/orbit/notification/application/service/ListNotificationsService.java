package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.query.ListNotificationsUseCase;
import com.orbit.notification.application.port.in.query.dto.ListNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationInfo;
import com.orbit.notification.application.port.in.query.dto.NotificationPageInfo;
import com.orbit.notification.application.port.out.NotificationQueryPort;
import com.orbit.shared.error.BusinessException;

/**
 * 요청 계정이 받은 알림을 최신순(고정)으로 쪽을 나눠 돌려준다. 숨긴 알림은 빼고, 안 읽은 알림만 고를 수 있다. 쪽 입력이 범위를 벗어나면 400(NOTIFICATION-001)이다.
 * 저장소가 목록과 전체 수를 따로 읽어도 같은 시점을 보도록 한 스냅샷(REPEATABLE READ)에서 읽는다.
 */
@Service
public class ListNotificationsService implements ListNotificationsUseCase {

    static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationQueryPort notificationQueryPort;

    public ListNotificationsService(NotificationQueryPort notificationQueryPort) {
        this.notificationQueryPort = notificationQueryPort;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NotificationPageInfo list(ListNotificationsQuery query) {
        Long accountId = Objects.requireNonNull(query.accountId(), "accountId must not be null");
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_PAGE_SIZE : query.size();
        if (page < 0
                || size < 1
                || size > NotificationQueryPort.MAX_SIZE
                || (long) page * size > NotificationQueryPort.MAX_OFFSET) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_INPUT);
        }
        NotificationQueryPort.VisibleNotifications result =
                notificationQueryPort.listVisible(accountId, query.unreadOnly(), page, size);
        return new NotificationPageInfo(
                result.notifications().stream().map(NotificationInfo::from).toList(), result.totalCount(), page, size);
    }
}
