package com.orbit.notification.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.domain.RetryBackoff;

@DisplayName("기사 앱 푸시 발신 대기열 설정")
class PushDeliveryPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 100건씩 집고, 5분 뒤 점유가 만료되며, 30초부터 30분까지 기다렸다 다시 보낸다")
    void defaults() {
        PushDeliveryProperties properties = new PushDeliveryProperties(null, null, null, null);

        assertThat(properties.batchSize()).isEqualTo(100);
        assertThat(properties.inFlightTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.toCommand().backoff())
                .isEqualTo(new RetryBackoff(Duration.ofSeconds(30), Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("집는 건수·점유 시간이 1 미만이거나 대기 시간이 잘못되면 기동하지 않는다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new PushDeliveryProperties(0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PushDeliveryProperties(null, Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PushDeliveryProperties(null, null, Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
