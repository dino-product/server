package com.orbit.notification.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.in.query.CountUnreadNotificationsUseCase;
import com.orbit.notification.application.port.in.query.dto.CountUnreadNotificationsQuery;
import com.orbit.notification.application.port.out.NotificationQueryPort;

/** 알림 아이콘 배지에 보일, 요청 계정이 받은 안 읽은 알림 수. 숨긴 알림은 세지 않는다. */
@Service
public class CountUnreadNotificationsService implements CountUnreadNotificationsUseCase {

    private final NotificationQueryPort notificationQueryPort;

    public CountUnreadNotificationsService(NotificationQueryPort notificationQueryPort) {
        this.notificationQueryPort = notificationQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public long count(CountUnreadNotificationsQuery query) {
        return notificationQueryPort.countVisibleUnread(
                Objects.requireNonNull(query.accountId(), "accountId must not be null"));
    }
}
