package com.orbit.notification.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;

/** 알림센터의 알림 한 줄. 작업과 무관한 알림이면 workId가, 참여 요청 결과가 아니면 participationRequestId가 null이다. */
public record NotificationInfo(
        Long id,
        NotificationType type,
        Long organizationId,
        Long workId,
        Long participationRequestId,
        String message,
        boolean read,
        Instant notifiedAt) {

    public static NotificationInfo from(Notification notification) {
        return new NotificationInfo(
                notification.id().orElseThrow(() -> new IllegalArgumentException("notification must be saved")),
                notification.type(),
                notification.organizationId(),
                notification.workId().orElse(null),
                notification.participationRequestId().orElse(null),
                notification.message(),
                notification.isRead(),
                notification.notifiedAt());
    }
}
