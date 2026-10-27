package com.orbit.shared.internal.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 실패로 남은 이벤트 발행 수와 그중 최대 처리 시도 횟수를 다 써 {@link FailedEventPublicationResubmitter}가 더 보내지 않는 발행 수를 지표로 내보낸다. 값은 수집할
 * 때마다 기록 테이블에서 센다. 모든 인스턴스가 같은 테이블을 세므로 인스턴스끼리 더하지 않는다.
 */
@Component
class FailedEventPublicationMetrics implements MeterBinder {

    private static final String COUNT_FAILED = "select count(*) from event_publication where status = 'FAILED'";
    private static final String COUNT_EXHAUSTED =
            "select count(*) from event_publication where status = 'FAILED' and completion_attempts >= ?";

    private final JdbcTemplate jdbcTemplate;
    private final EventResubmissionProperties properties;

    FailedEventPublicationMetrics(JdbcTemplate jdbcTemplate, EventResubmissionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("event.publications.failed", this, FailedEventPublicationMetrics::countFailed)
                .description("실패로 남은 이벤트 발행 수")
                .register(registry);
        Gauge.builder("event.publications.exhausted", this, FailedEventPublicationMetrics::countExhausted)
                .description("최대 처리 시도 횟수를 다 써 더 다시 보내지 않는 실패 이벤트 발행 수")
                .register(registry);
    }

    private double countFailed() {
        return jdbcTemplate.queryForObject(COUNT_FAILED, Long.class);
    }

    private double countExhausted() {
        return jdbcTemplate.queryForObject(COUNT_EXHAUSTED, Long.class, properties.maxAttempts());
    }
}
