package com.orbit.notification.adapter.out.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.application.port.out.PushSender;

import lombok.extern.slf4j.Slf4j;

/**
 * FCM을 끈 환경의 PushSender. 기기로 보내지 않고 보내려던 알림만 기록한 뒤 보낸 것으로 돌려준다. 문구에는 작업명·거절 사유가 들어가고 기기 토큰은 그 기기로
 * 푸시를 보낼 수 있는 값이라 둘 다 기록하지 않는다. {@code local}·{@code test}·{@code dev} 프로필에서 FCM을 껐을 때만 등록한다. 운영({@code prod})은
 * FCM을 켜 두며, 그 밖의 환경에서 FCM을 끄면 발신기가 없어 기동하지 않는다.
 */
@Slf4j
@Component
@Profile({"local & !prod", "test & !prod", "dev & !prod"})
@ConditionalOnProperty(
        prefix = "app.notification.push.fcm",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
class LoggingPushSender implements PushSender {

    @Override
    public PushSendResult send(PushMessage message, String deviceToken) {
        log.info(
                "Push notification skipped (FCM disabled): deliveryId={}, notificationId={}, accountId={}, type={}",
                message.deliveryId().value(),
                message.notificationId(),
                message.recipientAccountId(),
                message.type());
        return new PushSendResult.Sent("logged-" + message.deliveryId().value());
    }
}
