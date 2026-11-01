package com.orbit.shared.internal.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실패한 이벤트 발행 재전송 설정. 주기와 켜기·끄기는 {@link FailedEventPublicationResubmitScheduler}가 읽는다. 재전송은 횟수 제한 없이 처리될 때까지
 * 계속하며, 실패한 시도마다 다음 재전송까지 기다리는 시간을 두 배로 늘린다.
 *
 * @param initialBackoff 첫 처리가 실패한 뒤 다시 보내기까지 기다리는 시간. 없으면 1분, 양수
 * @param maxBackoff 기다리는 시간의 상한. 없으면 30분, {@code initialBackoff} 이상
 */
@ConfigurationProperties(prefix = "app.events.resubmission")
public record EventResubmissionProperties(Duration initialBackoff, Duration maxBackoff) {

    static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMinutes(1);
    static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(30);

    public EventResubmissionProperties {
        initialBackoff = initialBackoff == null ? DEFAULT_INITIAL_BACKOFF : initialBackoff;
        maxBackoff = maxBackoff == null ? DEFAULT_MAX_BACKOFF : maxBackoff;
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("app.events.resubmission.initial-backoff must be positive");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "app.events.resubmission.max-backoff must not be shorter than initial-backoff");
        }
    }

    /** 처리를 {@code attempts}번 시도해 실패한 발행을 마지막 시도 뒤 다시 보내기까지 기다리는 시간. */
    Duration backoffAfter(int attempts) {
        Duration backoff = initialBackoff;
        for (int attempt = 1; attempt < attempts && backoff.compareTo(maxBackoff) < 0; attempt++) {
            backoff = backoff.multipliedBy(2);
        }
        return backoff.compareTo(maxBackoff) < 0 ? backoff : maxBackoff;
    }
}
