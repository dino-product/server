package com.orbit.schedule.adapter.in.web.board;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 진행 보드 조회 응답 설정. 잘못된 값은 기동 시점에 거부한다. DB에서 보드를 동시에 만드는 수 상한은 같은 접두사의 스냅샷 어댑터 설정({@code
 * max-concurrent-builds})이 맡는다.
 *
 * @param maxConcurrentRequests 인스턴스가 동시에 받는 보드 조회 수 상한. 넘는 요청은 가입 공통 검사(DB) 전에 기다리지 않고 거절한다. 저장해 둔 보드 응답과
 *     같은 보드를 기다리는 요청까지 세며, 요청 스레드와 커넥션 풀을 다른 API에 남기도록 톰캣 스레드 수보다 작게 둔다. 없으면 40
 * @param retryAfter 거절할 때(503) 다시 조회하기까지 기다리라고 {@code Retry-After}로 알리는 시간. 없으면 2초
 */
@ConfigurationProperties(prefix = "app.schedule.progress-board")
public record ProgressBoardProperties(Integer maxConcurrentRequests, Duration retryAfter) {

    public ProgressBoardProperties {
        maxConcurrentRequests = maxConcurrentRequests == null ? 40 : maxConcurrentRequests;
        retryAfter = retryAfter == null ? Duration.ofSeconds(2) : retryAfter;
        if (maxConcurrentRequests < 1 || retryAfter.toSeconds() < 1) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive and retryAfter at least 1s");
        }
    }
}
