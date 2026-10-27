package com.orbit.realtime.adapter.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

/**
 * 정상으로 끝난 실시간 연결 요청을 HTTP 요청 시간 지표({@code http.server.requests})에서 뺀다. 연결 수명(30분 안팎) 동안 이어진 요청이 분위수와 경보를
 * 흐리기 때문이다. 거부·오류로 끝난 요청은 다른 요청처럼 남긴다. 연결 수와 닫힌 사유는 {@code realtime.connections} 지표로 본다.
 */
@Configuration(proxyBeanMethods = false)
class RealtimeMetricsConfig {

    @Bean
    MeterFilter realtimeStreamRequestTimerFilter() {
        return MeterFilter.deny(id -> "http.server.requests".equals(id.getName())
                && RealtimeStreamController.PATH.equals(id.getTag("uri"))
                && "SUCCESS".equals(id.getTag("outcome")));
    }
}
