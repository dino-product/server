package com.orbit.schedule.adapter.out.board;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 진행 보드 캐시 설정. 캐시는 로그인·실시간용 Redis와 다른, 가득 차면 오래된 키부터 지우는 캐시 전용 Redis에 둔다. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param enabled 캐시를 쓸지. 끄면 캐시 Redis에 연결하지 않고 조회마다 DB에서 만든다. 없으면 켬(배포 프로필은 끔)
 * @param redisHost 캐시 Redis 주소. 비우면 {@code sharedRedisFallback}일 때만 기본 Redis를 쓴다
 * @param redisPort 캐시 Redis 포트. 없으면 6379
 * @param sharedRedisFallback 주소가 없을 때 기본 Redis를 써도 되는지. 로컬·테스트용이며 배포 프로필은 거부한다. 없으면 허용
 * @param ttl 저장한 보드를 두는 시간. 무효화가 실패했을 때 옛 보드가 남는 상한이다. 없으면 60초
 * @param timeout 캐시 Redis 연결·명령 한도. 넘으면 캐시 없이 DB로 만든다. 없으면 200ms
 * @param unavailableBackoff 캐시 Redis 명령이 실패한 뒤 조회·저장을 건너뛰는 시간(무효화는 건너뛰지 않음). 없으면 5초
 * @param maxPeriods 조직마다 저장하는 조회 구간 수 상한. 넘는 구간은 저장하지 않는다. 없으면 8
 * @param maxValueSize 저장하는 보드 하나의 크기 상한. 넘으면 저장하지 않는다. 없으면 256KB
 */
@ConfigurationProperties(prefix = "app.schedule.board-cache")
public record BoardCacheProperties(
        Boolean enabled,
        String redisHost,
        Integer redisPort,
        Boolean sharedRedisFallback,
        Duration ttl,
        Duration timeout,
        Duration unavailableBackoff,
        Integer maxPeriods,
        DataSize maxValueSize) {

    public BoardCacheProperties {
        enabled = enabled == null || enabled;
        redisHost = redisHost == null ? "" : redisHost.strip();
        redisPort = redisPort == null ? 6379 : redisPort;
        sharedRedisFallback = sharedRedisFallback == null || sharedRedisFallback;
        ttl = ttl == null ? Duration.ofSeconds(60) : ttl;
        timeout = timeout == null ? Duration.ofMillis(200) : timeout;
        unavailableBackoff = unavailableBackoff == null ? Duration.ofSeconds(5) : unavailableBackoff;
        maxPeriods = maxPeriods == null ? 8 : maxPeriods;
        maxValueSize = maxValueSize == null ? DataSize.ofKilobytes(256) : maxValueSize;
        if (redisPort < 1
                || ttl.toMillis() < 1
                || timeout.toMillis() < 1
                || unavailableBackoff.isNegative()
                || maxPeriods < 1
                || maxValueSize.toBytes() < 1) {
            throw new IllegalArgumentException("board cache settings must be positive");
        }
    }
}
