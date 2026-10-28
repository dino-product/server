package com.orbit.notification.application.port.out;

import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationDelivery;
import com.orbit.notification.domain.NotificationDeliveryId;
import com.orbit.notification.domain.NotificationType;

/**
 * 보낼 푸시 한 건. 알림 식별자는 앱이 탭한 알림을 읽음으로 바꾸고 같은 알림을 다시 보냈을 때 기기에서 하나로 덮어쓰는 데 쓴다. 문구에는 작업명·거절 사유가 들어가므로
 * 구현은 문구를 로그에 남기지 않는다.
 *
 * @param workId 탭했을 때 이동할 작업. 작업과 무관한 알림이면 {@code null}
 * @param participationRequestId 탭했을 때 이동할 참여 요청. 참여 요청 결과 알림이 아니면 {@code null}
 * @param attempt 이번이 몇 번째 발신 시도인지
 */
public record PushMessage(
        NotificationDeliveryId deliveryId,
        Long notificationId,
        Long recipientAccountId,
        NotificationType type,
        String message,
        Long workId,
        Long participationRequestId,
        int attempt) {

    public static PushMessage of(NotificationDelivery delivery, Notification notification) {
        return new PushMessage(
                delivery.id().orElseThrow(() -> new IllegalArgumentException("delivery must be saved")),
                delivery.notificationId(),
                delivery.recipientAccountId(),
                notification.type(),
                notification.message(),
                notification.workId().orElse(null),
                notification.participationRequestId().orElse(null),
                delivery.attempts());
    }
}
