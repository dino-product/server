package com.orbit.schedule.adapter.in.web.board;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 진행 보드 조회 응답 설정. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param maxConcurrentRequests 인스턴스가 동시에 처리하는 보드 조회 수 상한. 넘는 요청은 기다리지 않고 거절한다. 조회마다 DB 커넥션 하나를 쓰므로 커넥션 풀
 *     크기보다 조금 크게 둔다. 없으면 12
 * @param retryAfter 거절할 때(503) 다시 조회하기까지 기다리라고 {@code Retry-After}로 알리는 시간. 없으면 2초
 */
@ConfigurationProperties(prefix = "app.schedule.progress-board")
public record ProgressBoardProperties(Integer maxConcurrentRequests, Duration retryAfter) {

    public ProgressBoardProperties {
        maxConcurrentRequests = maxConcurrentRequests == null ? 12 : maxConcurrentRequests;
        retryAfter = retryAfter == null ? Duration.ofSeconds(2) : retryAfter;
        if (maxConcurrentRequests < 1 || retryAfter.toSeconds() < 1) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive and retryAfter at least 1s");
        }
    }
}
