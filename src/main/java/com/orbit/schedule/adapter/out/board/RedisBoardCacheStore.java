package com.orbit.schedule.adapter.out.board;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.orbit.schedule.domain.OrganizationId;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.ClientResources;

/**
 * 캐시 전용 Redis에 둔 {@link BoardCacheStore}. 연결은 이 저장소만 쓰도록 안에서 만든다. 연결 팩토리나 템플릿을 Bean으로 등록하면 기본 Redis 자동 설정이
 * 물러나 로그인·실시간이 캐시 Redis를 쓰게 되기 때문이다. 연결·명령은 짧은 한도 안에 끝나지 않으면 실패하고, 연결이 끊긴 동안의 명령은 기다리지 않고 거부한다.
 *
 * <ul>
 *   <li>보드: {@value #BOARD_KEY_PREFIX}{조직} Hash, 필드는 조회 구간. TTL은 키를 처음 만들 때 걸어, 어느 구간도 TTL보다 오래 남지 않는다.
 *       키의 {@code v1}은 값 형식 버전이라, 형식을 바꾸면 올려 배포 중 새·옛 인스턴스가 서로의 값을 읽지 않게 한다.
 *   <li>세대: {@value #GENERATION_KEY_PREFIX}{조직} 정수. 키가 없으면(처음이거나 캐시 Redis가 지웠으면) 읽기·무효화가 Redis 서버 시각(마이크로초)으로
 *       새로 심어, 앞서 읽은 세대와 겹치지 않게 한다. 저장은 세대 키가 없으면 하지 않는다.
 * </ul>
 */
class RedisBoardCacheStore implements BoardCacheStore, AutoCloseable {

    static final String BOARD_KEY_PREFIX = "orbit:schedule:board:v1:";
    static final String GENERATION_KEY_PREFIX = "orbit:schedule:board-gen:";
    static final Duration GENERATION_TTL = Duration.ofDays(7);

    /** 세대 키가 없으면(처음이거나 캐시 Redis가 지웠으면) 서버 시각으로 새로 심는다. 0처럼 다시 나올 수 있는 값을 쓰지 않는다. */
    private static final RedisScript<String> GENERATION = RedisScript.of("""
            local generation = redis.call('GET', KEYS[1])
            if not generation then
                local now = redis.call('TIME')
                generation = string.format('%.0f', now[1] * 1000000 + now[2])
                redis.call('SET', KEYS[1], generation, 'EX', ARGV[1])
            end
            return generation
            """, String.class);

    /** 세대 키가 없거나 다르면 저장하지 않는다. TTL은 키를 처음 만들 때만 걸어, 다른 구간을 저장해도 옛 구간이 오래 남지 않게 한다. */
    private static final RedisScript<Long> STORE = RedisScript.of("""
            local generation = redis.call('GET', KEYS[1])
            if (not generation) or generation ~= ARGV[1] then return 0 end
            if redis.call('HEXISTS', KEYS[2], ARGV[2]) == 0
                    and redis.call('HLEN', KEYS[2]) >= tonumber(ARGV[5]) then return -1 end
            redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[4], 'NX')
            return 1
            """, Long.class);

    private static final RedisScript<Long> INVALIDATE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                local now = redis.call('TIME')
                redis.call('SET', KEYS[1], string.format('%.0f', now[1] * 1000000 + now[2]))
            end
            redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    private final LettuceConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final int maxPeriods;

    RedisBoardCacheStore(
            String host, int port, Duration timeout, Duration ttl, int maxPeriods, ClientResources clientResources) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        if (clientResources != null) {
            // 기본 Redis 연결과 이벤트 루프를 나눠 쓴다. 공유 자원이라 이 저장소를 닫아도 멈추지 않는다.
            builder.clientResources(clientResources);
        }
        LettuceClientConfiguration clientConfiguration = builder.commandTimeout(timeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(
                                SocketOptions.builder().connectTimeout(timeout).build())
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .timeoutOptions(TimeoutOptions.enabled())
                        .build())
                .build();
        this.connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        this.redisTemplate = new StringRedisTemplate(connectionFactory);
        this.ttl = ttl;
        this.maxPeriods = maxPeriods;
    }

    @Override
    public Optional<String> find(OrganizationId organizationId, String period) {
        Object value = redisTemplate.opsForHash().get(boardKey(organizationId), period);
        return Optional.ofNullable((String) value);
    }

    @Override
    public long generation(OrganizationId organizationId) {
        String value = redisTemplate.execute(
                GENERATION, List.of(generationKey(organizationId)), Long.toString(GENERATION_TTL.toSeconds()));
        if (value == null) {
            throw new IllegalStateException("board cache generation script returned no result");
        }
        return Long.parseLong(value);
    }

    @Override
    public StoreResult store(OrganizationId organizationId, String period, long generation, String value) {
        Long result = redisTemplate.execute(
                STORE,
                List.of(generationKey(organizationId), boardKey(organizationId)),
                Long.toString(generation),
                period,
                value,
                Long.toString(ttl.toMillis()),
                Integer.toString(maxPeriods));
        if (result == null) {
            throw new IllegalStateException("board cache store script returned no result");
        }
        return switch (result.intValue()) {
            case 1 -> StoreResult.STORED;
            case 0 -> StoreResult.GENERATION_CHANGED;
            default -> StoreResult.TOO_MANY_PERIODS;
        };
    }

    @Override
    public void invalidate(OrganizationId organizationId) {
        redisTemplate.execute(
                INVALIDATE,
                List.of(generationKey(organizationId), boardKey(organizationId)),
                Long.toString(GENERATION_TTL.toSeconds()));
    }

    @Override
    public void close() {
        connectionFactory.destroy();
    }

    static String boardKey(OrganizationId organizationId) {
        return BOARD_KEY_PREFIX + organizationId.value();
    }

    static String generationKey(OrganizationId organizationId) {
        return GENERATION_KEY_PREFIX + organizationId.value();
    }
}
