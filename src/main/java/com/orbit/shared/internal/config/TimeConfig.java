package com.orbit.shared.internal.config;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** UTC 시계. 저장소(PostgreSQL)가 담는 마이크로초 정밀도로 끊어, 기록한 시각이 저장 전후에 같게 비교되게 한다. */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
