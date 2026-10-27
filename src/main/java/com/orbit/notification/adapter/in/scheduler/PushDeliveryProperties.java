package com.orbit.notification.adapter.in.scheduler;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;
import com.orbit.notification.domain.RetryBackoff;

/**
 * 기사 앱 푸시 발신 대기열 설정. 주기와 켜기·끄기는 {@link PushDeliveryDispatchScheduler}가 읽는다. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param batchSize 한 번에 집는 최대 건수. 없으면 100, 1 이상
 * @param inFlightTimeout 집은 발신의 점유 시간. 없으면 5분, 양수
 * @param initialBackoff 첫 실패 뒤 다시 보내기까지 기다리는 시간. 없으면 30초
 * @param maxBackoff 기다리는 시간의 상한. 없으면 30분, {@code initialBackoff} 이상
 */
@ConfigurationProperties(prefix = "app.notification.push-delivery")
public record PushDeliveryProperties(
        Integer batchSize, Duration inFlightTimeout, Duration initialBackoff, Duration maxBackoff) {

    static final int DEFAULT_BATCH_SIZE = 100;
    static final Duration DEFAULT_IN_FLIGHT_TIMEOUT = Duration.ofMinutes(5);
    static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(30);
    static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(30);

    public PushDeliveryProperties {
        batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        inFlightTimeout = inFlightTimeout == null ? DEFAULT_IN_FLIGHT_TIMEOUT : inFlightTimeout;
        initialBackoff = initialBackoff == null ? DEFAULT_INITIAL_BACKOFF : initialBackoff;
        maxBackoff = maxBackoff == null ? DEFAULT_MAX_BACKOFF : maxBackoff;
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.notification.push-delivery.batch-size must be at least 1");
        }
        if (inFlightTimeout.isNegative() || inFlightTimeout.isZero()) {
            throw new IllegalArgumentException("app.notification.push-delivery.in-flight-timeout must be positive");
        }
        // 대기 시간 값의 검사는 RetryBackoff가 맡는다.
        new RetryBackoff(initialBackoff, maxBackoff);
    }

    DispatchPushDeliveriesCommand toCommand() {
        return new DispatchPushDeliveriesCommand(
                batchSize, inFlightTimeout, new RetryBackoff(initialBackoff, maxBackoff));
    }
}
