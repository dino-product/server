package com.orbit.notification.application.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.error.NotificationErrorCode;
import com.orbit.notification.application.port.in.command.ReadNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.ReadNotificationCommand;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.domain.Notification;
import com.orbit.shared.error.BusinessException;

/**
 * 요청 계정이 받은 알림을 읽음으로 바꾼다. 없는 알림은 404(NOTIFICATION-002), 다른 계정이 받은 알림은 403(NOTIFICATION-003)이다. 본인 알림이라도 숨긴
 * 알림은 알림센터 어디에도 보이지 않으므로 없는 알림과 같이 404다. 이미 읽었으면 그대로 성공한다.
 */
@Service
public class ReadNotificationService implements ReadNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public ReadNotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void read(ReadNotificationCommand command) {
        Long accountId = Objects.requireNonNull(command.accountId(), "accountId must not be null");
        Notification notification = notificationRepository
                .findById(Objects.requireNonNull(command.notificationId(), "notificationId must not be null"))
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isReceivedBy(accountId)) {
            throw new BusinessException(NotificationErrorCode.NOT_NOTIFICATION_RECIPIENT);
        }
        if (notification.isHidden()) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (notification.isRead()) {
            return;
        }
        notification.markRead(clock.instant());
        notificationRepository.save(notification);
    }
}
