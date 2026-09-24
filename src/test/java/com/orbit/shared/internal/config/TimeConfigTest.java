package com.orbit.shared.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class TimeConfigTest {

    @Test
    void clockTicksInUtcMicroseconds() {
        assertThat(new TimeConfig().clock()).isEqualTo(Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)));
    }
}
