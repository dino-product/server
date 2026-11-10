package com.orbit.notification.adapter.out.persistence;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.out.NotificationDeliveryRepository;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationDelivery;

/**
 * {@link PushNotificationPort}의 구현. 받은 푸시를 보내지 않고 알림을 저장하는 트랜잭션에 발신 대기로 넣어, 알림이 커밋돼야 보내지고 롤백되면 함께 사라지게 한다.
 * 실제 발신은 디스패처가 커밋 뒤에 한다.
 */
@Component
class PushNotificationQueueAdapter implements PushNotificationPort {

    private final NotificationDeliveryRepository deliveries;
    private final Clock clock;

    PushNotificationQueueAdapter(NotificationDeliveryRepository deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void send(Notification notification) {
        Long notificationId = notification
                .id()
                .orElseThrow(() -> new IllegalArgumentException("only saved notifications can be pushed"));
        deliveries.add(
                NotificationDelivery.pendingPush(notificationId, notification.recipientAccountId(), clock.instant()));
    }
}
