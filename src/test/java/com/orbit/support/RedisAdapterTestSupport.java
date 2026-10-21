package com.orbit.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring Context 없이 실제 Redis 컨테이너로 저장소 어댑터를 검사한다. 컨테이너는 Spring 테스트와 같은 {@link SharedContainers#REDIS}를 쓰므로, 클래스를
 * 시작할 때 앞선 테스트 클래스가 남긴 키를 지운다.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class RedisAdapterTestSupport {

    private static LettuceConnectionFactory connectionFactory;
    protected static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connectRedis() {
        GenericContainer<?> redis = SharedContainers.REDIS;
        redis.start();
        connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(SharedContainers.REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterAll
    static void disconnectRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
}
