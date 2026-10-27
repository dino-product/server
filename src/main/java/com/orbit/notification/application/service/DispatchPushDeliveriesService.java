package com.orbit.notification.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.orbit.notification.application.port.in.command.DispatchPushDeliveriesUseCase;
import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;
import com.orbit.notification.application.port.out.NotificationDeliveryRepository;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.application.port.out.PushSender;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationDelivery;
import com.orbit.notification.domain.RetryBackoff;

import lombok.extern.slf4j.Slf4j;

/**
 * 보낼 때가 된 기사 앱 푸시 발신 대기를 집어 하나씩 보내고 결과를 기록한다. 트랜잭션을 두지 않는다. 집기와 결과 저장은 저장소가 각각 짧게 커밋하고, 그 사이의 외부
 * 발신은 트랜잭션·DB 연결을 붙잡지 않는다. 결과를 저장하기 전에 인스턴스가 멈추면 발신 중으로 남은 행은 점유가 만료된 뒤 다시 보내지므로 드물게 두 번 갈 수 있다.
 *
 * <p>보내기 직전에 알림을 다시 읽는다. 기다리는 동안 거절로 숨겨진 알림은 보내지 않고 건너뛰고, 알림이 없으면 실패로 끝낸다.
 */
@Slf4j
@Service
public class DispatchPushDeliveriesService implements DispatchPushDeliveriesUseCase {

    private final NotificationDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final PushSender pushSender;
    private final Clock clock;

    public DispatchPushDeliveriesService(
            NotificationDeliveryRepository deliveries,
            NotificationRepository notifications,
            PushSender pushSender,
            Clock clock) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Override
    public void dispatch(DispatchPushDeliveriesCommand command) {
        Instant now = clock.instant();
        for (NotificationDelivery claimed :
                deliveries.claimDue(now, now.plus(command.inFlightTimeout()), command.batchSize())) {
            NotificationDelivery completed = deliver(claimed, command.backoff());
            if (!deliveries.saveResult(completed)) {
                log.warn(
                        "Discarded push delivery result: delivery {} was claimed again after its lease expired",
                        claimed.id().orElseThrow().value());
            }
        }
    }

    private NotificationDelivery deliver(NotificationDelivery delivery, RetryBackoff backoff) {
        Optional<Notification> notification = notifications.findById(delivery.notificationId());
        if (notification.isEmpty()) {
            log.warn(
                    "Push delivery {} failed: notification {} not found",
                    delivery.id().orElseThrow().value(),
                    delivery.notificationId());
            return delivery.fail("notification not found");
        }
        if (notification.get().isHidden()) {
            return delivery.skip("notification hidden");
        }
        return complete(delivery, send(delivery, notification.get()), backoff);
    }

    private PushSendResult send(NotificationDelivery delivery, Notification notification) {
        try {
            return pushSender.send(PushMessage.of(delivery, notification));
        } catch (RuntimeException e) {
            log.warn(
                    "Push sender threw for delivery {}",
                    delivery.id().orElseThrow().value(),
                    e);
            return new PushSendResult.RetryableFailure(e.toString());
        }
    }

    private NotificationDelivery complete(NotificationDelivery delivery, PushSendResult result, RetryBackoff backoff) {
        Instant now = clock.instant();
        return switch (result) {
            case PushSendResult.Sent sent -> delivery.markSent(sent.providerMessageId(), now);
            case PushSendResult.RetryableFailure failure -> {
                Instant retryAt = now.plus(backoff.delayAfter(delivery.attempts(), ThreadLocalRandom.current()));
                log.warn(
                        "Push delivery {} failed on attempt {}, retrying at {}: {}",
                        delivery.id().orElseThrow().value(),
                        delivery.attempts(),
                        retryAt,
                        failure.reason());
                yield delivery.retryAt(failure.reason(), retryAt);
            }
            case PushSendResult.PermanentFailure failure -> {
                log.warn(
                        "Push delivery {} failed permanently: {}",
                        delivery.id().orElseThrow().value(),
                        failure.reason());
                yield delivery.fail(failure.reason());
            }
            case PushSendResult.NoReachableDevice noDevice -> delivery.skip(noDevice.reason());
        };
    }
}
