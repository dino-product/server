package com.orbit.shared.internal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("실패한 이벤트 발행 재전송 설정")
class EventResubmissionPropertiesTest {

    @Test
    @DisplayName("대기 시간 설정이 없으면 1분에서 시작해 30분까지 늘린다")
    void defaultsToOneMinuteUpToThirtyMinutes() {
        EventResubmissionProperties properties = new EventResubmissionProperties(null, null);

        assertThat(properties.initialBackoff()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.maxBackoff()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("실패한 시도마다 다음 재전송까지 기다리는 시간을 두 배로 늘리고 상한에서 멈춘다")
    void doublesBackoffPerAttemptUpToMax() {
        EventResubmissionProperties properties =
                new EventResubmissionProperties(Duration.ofMinutes(1), Duration.ofMinutes(30));

        assertThat(properties.backoffAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.backoffAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.backoffAfter(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(properties.backoffAfter(5)).isEqualTo(Duration.ofMinutes(16));
        assertThat(properties.backoffAfter(6)).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.backoffAfter(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("대기 시간이 0 이하이거나 상한이 첫 대기 시간보다 짧으면 기동하지 않는다")
    void rejectsNonPositiveOrInvertedBackoff() {
        assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ofSeconds(-1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ofMinutes(5), Duration.ofMinutes(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
