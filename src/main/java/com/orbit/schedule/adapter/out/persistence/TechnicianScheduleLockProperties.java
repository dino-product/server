package com.orbit.schedule.adapter.out.persistence;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기사 일정 잠금 설정. 대기 중인 요청은 DB 연결을 붙잡고 기다리므로, 한도를 길게 잡으면 한 기사에 요청이 몰릴 때 연결 풀이 먼저 바닥날 수 있어 상한을 둔다. 잘못된
 * 값은 요청마다 실패하지 않도록 기동 시점에 거부한다.
 *
 * @param waitLimit 같은 기사의 앞선 변경을 기다리는 최대 시간. 없으면 2초, 1ms 이상 30초 이하
 */
@ConfigurationProperties(prefix = "app.schedule.technician-lock")
public record TechnicianScheduleLockProperties(Duration waitLimit) {

    static final Duration DEFAULT_WAIT_LIMIT = Duration.ofSeconds(2);
    static final Duration MAX_WAIT_LIMIT = Duration.ofSeconds(30);

    public TechnicianScheduleLockProperties {
        waitLimit = waitLimit == null ? DEFAULT_WAIT_LIMIT : waitLimit;
        if (waitLimit.toMillis() < 1 || waitLimit.compareTo(MAX_WAIT_LIMIT) > 0) {
            throw new IllegalArgumentException("waitLimit must be between 1ms and " + MAX_WAIT_LIMIT);
        }
    }
}
