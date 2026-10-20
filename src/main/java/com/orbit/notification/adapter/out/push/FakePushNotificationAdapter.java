package com.orbit.notification.adapter.out.push;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;

import lombok.extern.slf4j.Slf4j;

/**
 * 임시 PushNotificationPort 구현. 기기 토큰과 푸시 제공자(FCM)가 아직 없어 보내지 않고 보내려던 알림만 기록한다. 문구에는 작업명·거절 사유가 들어가므로
 * 기록하지 않는다. 실제 발송 어댑터를 추가하는 작업(notification 문서 N-21, 추적 이슈 미등록)에서 교체한 뒤 삭제한다. {@code local}·{@code test}·{@code dev}
 * 프로필에서만 등록하며 운영({@code prod})에는 등록하지 않는다.
 */
@Slf4j
@Component
@Profile({"local & !prod", "test & !prod", "dev & !prod"})
class FakePushNotificationAdapter implements PushNotificationPort {

    @Override
    public void send(Notification notification) {
        log.info(
                "Push notification skipped (no push provider): notificationId={}, accountId={}, type={}",
                notification.id().orElse(null),
                notification.recipientAccountId(),
                notification.type());
    }
}
