package com.orbit.notification.domain;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * 실패한 발신을 다시 보내기까지 기다리는 시간. 첫 실패 뒤 {@code initial}에서 시작해 실패할 때마다 두 배로 늘고 {@code max}에서 멈춘다. 횟수 제한은 없다.
 *
 * @param initial 첫 실패 뒤 기다리는 시간. 양수
 * @param max 기다리는 시간의 상한. {@code initial} 이상
 */
public record RetryBackoff(Duration initial, Duration max) {

    public RetryBackoff {
        if (initial == null || initial.isNegative() || initial.isZero()) {
            throw new IllegalArgumentException("initial backoff must be positive");
        }
        if (max == null || max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("max backoff must not be shorter than initial backoff");
        }
    }

    /**
     * {@code failedAttempts}번째 실패 뒤 기다릴 시간. 같은 시각에 실패한 발신들이 한꺼번에 다시 몰리지 않도록 계산한 시간의 80% 이상 100% 이하에서 고른다.
     */
    public Duration delayAfter(int failedAttempts, RandomGenerator random) {
        Duration delay = initial;
        for (int attempt = 1; attempt < failedAttempts && delay.compareTo(max) < 0; attempt++) {
            delay = delay.multipliedBy(2);
        }
        if (delay.compareTo(max) > 0) {
            delay = max;
        }
        return delay.minusNanos(random.nextLong(delay.toNanos() / 5 + 1));
    }
}
