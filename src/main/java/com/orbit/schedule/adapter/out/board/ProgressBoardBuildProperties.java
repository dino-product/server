package com.orbit.schedule.adapter.out.board;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 진행 보드를 DB에서 만드는 일의 설정. 잘못된 값은 기동 시점에 거부한다. 같은 접두사의 {@code max-concurrent-requests}·{@code retry-after}는
 * 웹 어댑터의 요청 수 상한 설정이다.
 *
 * @param maxConcurrentBuilds 인스턴스가 동시에 DB에서 만드는 보드 수 상한. 넘는 요청은 기다리지 않고 거절한다. 만들 때마다 DB 커넥션 하나를 쓰므로 커넥션
 *     풀 크기보다 조금 크게 둔다. 없으면 12
 * @param joinTimeout 같은 보드를 만드는 다른 요청의 결과를 기다리는 한도. 넘으면 거절한다. 없으면 3초(DB 커넥션 대기 한도와 같다)
 */
@ConfigurationProperties(prefix = "app.schedule.progress-board")
public record ProgressBoardBuildProperties(Integer maxConcurrentBuilds, Duration joinTimeout) {

    public ProgressBoardBuildProperties {
        maxConcurrentBuilds = maxConcurrentBuilds == null ? 12 : maxConcurrentBuilds;
        joinTimeout = joinTimeout == null ? Duration.ofSeconds(3) : joinTimeout;
        if (maxConcurrentBuilds < 1 || joinTimeout.isNegative() || joinTimeout.isZero()) {
            throw new IllegalArgumentException("maxConcurrentBuilds and joinTimeout must be positive");
        }
    }
}
