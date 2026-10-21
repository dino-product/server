package com.orbit.notification.application.port.out;

import java.util.List;

import com.orbit.notification.domain.Notification;

/** 알림센터 조회. 숨긴 알림은 어느 조회에도 나오지 않는다. */
public interface NotificationQueryPort {

    /** 한 쪽에 담을 수 있는 가장 많은 알림 수. */
    int MAX_SIZE = 100;

    /** 건너뛸 수 있는 가장 많은 알림 수. 깊은 offset은 저장소가 앞의 행을 모두 읽어야 하므로 막는다. */
    long MAX_OFFSET = 10_000;

    /**
     * 이 계정이 받은 알림을 알림 시각의 최신순(같으면 식별자 내림차순)으로 한 쪽 읽는다.
     *
     * @param page 0 이상. page × size는 {@link #MAX_OFFSET} 이하다
     * @param size 1 이상 {@link #MAX_SIZE} 이하
     */
    VisibleNotifications listVisible(Long accountId, boolean unreadOnly, int page, int size);

    /** 이 계정이 받은 안 읽은 알림 수. */
    long countVisibleUnread(Long accountId);

    /** 한 쪽의 알림과 조건에 맞는 전체 수. */
    record VisibleNotifications(List<Notification> notifications, long totalCount) {

        public VisibleNotifications {
            notifications = List.copyOf(notifications);
        }
    }
}
