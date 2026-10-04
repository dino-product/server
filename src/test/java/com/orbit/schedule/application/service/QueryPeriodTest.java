package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("조회 구간")
class QueryPeriodTest {

    private static final Duration DAY = Duration.ofDays(1);

    @Test
    @DisplayName("넓힌 구간은 조회 가능한 범위 밖으로 나가지 않는다")
    void widensWithinStorableRange() {
        Instant from = Instant.parse("2026-09-25T00:00:00Z");

        assertThat(QueryPeriod.of(from, from.plus(DAY)).widenedBy(DAY))
                .isEqualTo(new QueryPeriod(from.minus(DAY), from.plus(DAY).plus(DAY)));
        assertThat(QueryPeriod.of(QueryPeriod.EARLIEST, QueryPeriod.EARLIEST.plus(DAY))
                        .widenedBy(DAY)
                        .from())
                .isEqualTo(QueryPeriod.EARLIEST);
        assertThat(QueryPeriod.of(QueryPeriod.LATEST.minus(DAY), QueryPeriod.LATEST)
                        .widenedBy(DAY)
                        .to())
                .isEqualTo(QueryPeriod.LATEST);
    }
}
