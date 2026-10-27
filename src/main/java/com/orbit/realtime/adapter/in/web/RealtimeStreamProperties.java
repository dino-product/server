package com.orbit.realtime.adapter.in.web;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실시간 연결 응답 설정. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param emitterTimeout 연결 목록이 닫지 못한 요청을 끝내는 안전 한도. 연결 수명({@code app.realtime.connection.connection-lifetime})보다 길게
 *     둔다. 없으면 31분
 * @param retryAfter 연결을 받을 수 없을 때(503) 다시 연결하기까지 기다리라고 {@code Retry-After}로 알리는 시간. 없으면 30초
 */
@ConfigurationProperties(prefix = "app.realtime.stream")
public record RealtimeStreamProperties(Duration emitterTimeout, Duration retryAfter) {

    public RealtimeStreamProperties {
        emitterTimeout = emitterTimeout == null ? Duration.ofMinutes(31) : emitterTimeout;
        retryAfter = retryAfter == null ? Duration.ofSeconds(30) : retryAfter;
        if (emitterTimeout.toMillis() < 1 || retryAfter.toSeconds() < 1) {
            throw new IllegalArgumentException("emitterTimeout must be at least 1ms and retryAfter at least 1s");
        }
    }
}
