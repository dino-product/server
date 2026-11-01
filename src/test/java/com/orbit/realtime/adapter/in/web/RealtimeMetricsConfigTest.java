package com.orbit.realtime.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("실시간 연결 요청의 HTTP 요청 시간 지표")
class RealtimeMetricsConfigTest {

    private static final String STREAM_URI = "/api/v1/organizations/{organizationId}/events";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    @DisplayName("정상으로 끝난 실시간 연결 요청만 빼고 오류로 끝난 연결 요청과 다른 요청은 남긴다")
    void dropsOnlySuccessfulStreamRequests() {
        meters.config().meterFilter(new RealtimeMetricsConfig().realtimeStreamRequestTimerFilter());

        record(STREAM_URI, "SUCCESS");
        record(STREAM_URI, "CLIENT_ERROR");
        record("/api/v1/organizations/{organizationId}/works", "SUCCESS");

        assertThat(meters.find("http.server.requests")
                        .tag("uri", STREAM_URI)
                        .tag("outcome", "SUCCESS")
                        .timer())
                .isNull();
        assertThat(meters.find("http.server.requests")
                        .tag("uri", STREAM_URI)
                        .tag("outcome", "CLIENT_ERROR")
                        .timer())
                .isNotNull();
        assertThat(meters.find("http.server.requests")
                        .tag("uri", "/api/v1/organizations/{organizationId}/works")
                        .timer())
                .isNotNull();
    }

    private void record(String uri, String outcome) {
        meters.timer("http.server.requests", "uri", uri, "outcome", outcome).record(Duration.ofSeconds(1));
    }
}
