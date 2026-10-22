package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("발신 재시도 대기 시간")
class RetryBackoffTest {

    private static final RetryBackoff BACKOFF = new RetryBackoff(Duration.ofSeconds(30), Duration.ofMinutes(30));
    private static final RandomGenerator NO_JITTER = new FixedRandom(0);

    @Test
    @DisplayName("첫 실패 뒤 첫 대기 시간에서 시작해 실패마다 두 배로 늘리고 상한에서 멈춘다")
    void doublesPerFailedAttemptUpToMax() {
        assertThat(BACKOFF.delayAfter(1, NO_JITTER)).isEqualTo(Duration.ofSeconds(30));
        assertThat(BACKOFF.delayAfter(2, NO_JITTER)).isEqualTo(Duration.ofMinutes(1));
        assertThat(BACKOFF.delayAfter(3, NO_JITTER)).isEqualTo(Duration.ofMinutes(2));
        assertThat(BACKOFF.delayAfter(7, NO_JITTER)).isEqualTo(Duration.ofMinutes(30));
        assertThat(BACKOFF.delayAfter(Integer.MAX_VALUE, NO_JITTER)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("여러 발신이 같은 시각에 몰리지 않도록 계산한 시간의 80% 이상 100% 이하에서 고른다")
    void jittersWithinEightyToHundredPercent() {
        RandomGenerator maxJitter = new FixedRandom(Long.MAX_VALUE);

        assertThat(BACKOFF.delayAfter(2, maxJitter)).isBetween(Duration.ofSeconds(48), Duration.ofMinutes(1));
        for (int i = 0; i < 100; i++) {
            assertThat(BACKOFF.delayAfter(7, RandomGenerator.getDefault()))
                    .isBetween(Duration.ofMinutes(24), Duration.ofMinutes(30));
        }
    }

    @Test
    @DisplayName("대기 시간이 0 이하이거나 상한이 첫 대기 시간보다 짧으면 만들지 않는다")
    void rejectsNonPositiveOrInvertedBackoff() {
        assertThatThrownBy(() -> new RetryBackoff(Duration.ZERO, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryBackoff(Duration.ofMinutes(2), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryBackoff(null, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** {@code nextLong(bound)}가 항상 {@code bound - 1}과 {@code value} 중 작은 값을 돌려주는 시험용 난수. */
    private record FixedRandom(long value) implements RandomGenerator {

        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public long nextLong(long bound) {
            return Math.min(value, bound - 1);
        }
    }
}
