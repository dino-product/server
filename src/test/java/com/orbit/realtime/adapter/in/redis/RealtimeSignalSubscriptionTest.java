package com.orbit.realtime.adapter.in.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.support.RedisAdapterTestSupport;
import com.orbit.support.SharedContainers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/** 같은 Redis를 다른 테스트 컨텍스트도 구독할 수 있어, 이 테스트만 쓰는 발주사 식별자로 받은 신호를 고른다. */
@DisplayName("실시간 신호 채널 구독")
class RealtimeSignalSubscriptionTest extends RedisAdapterTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T01:00:00Z");
    private static final long ORGANIZATION_ID = 987_001L;

    private final List<RealtimeSignal> delivered = new CopyOnWriteArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            delivered::add, JsonMapper.builder().build(), meters);

    @Test
    @DisplayName("시작하면 채널을 구독해 받은 신호를 넘긴다")
    void subscribesAndDeliversSignals() throws Exception {
        RealtimeSignalSubscription subscription = subscription(redisTemplate.getConnectionFactory());
        subscription.start();
        try {
            // 시작은 첫 시도가 끝날 때까지 기다리므로 Redis가 있으면 바로 구독 중이다.
            assertThat(subscription.isListening()).isTrue();

            redisTemplate.convertAndSend(RedisRealtimeSignalSubscriber.CHANNEL, """
                    {"type":"work-changed","organizationId":%d,"workIds":[10],"occurredAt":"2026-10-26T01:00:00Z"}
                    """.formatted(ORGANIZATION_ID));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(delivered)
                    .contains(new RealtimeSignal.OrganizationWorksChanged(ORGANIZATION_ID, List.of(10L), OCCURRED_AT)));
        } finally {
            subscription.stop();
            subscription.destroy();
        }
    }

    @Test
    @DisplayName("구독 중이면 구독 여부 지표가 1이고, 멈추면 0이다")
    void reportsListeningAsGauge() throws Exception {
        RealtimeSignalSubscription subscription = subscription(redisTemplate.getConnectionFactory());
        try {
            subscription.start();
            assertThat(listeningGauge()).isEqualTo(1.0);
        } finally {
            subscription.stop();
            subscription.destroy();
        }
        assertThat(listeningGauge()).isZero();
    }

    @Test
    @DisplayName("Redis에 연결할 수 없어도 시작은 실패하지 않고 구독만 열리지 않는다")
    void startsWithoutRedis() throws Exception {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("localhost", 1);
        unreachable.afterPropertiesSet();
        unreachable.start();
        RealtimeSignalSubscription subscription = subscription(unreachable);
        try {
            subscription.start();

            assertThat(subscription.isRunning()).isTrue();
            assertThat(subscription.isListening()).isFalse();
            assertThat(listeningGauge()).isZero();
        } finally {
            subscription.stop();
            subscription.destroy();
            unreachable.destroy();
        }
        assertThat(subscription.isRunning()).isFalse();
    }

    @Test
    @DisplayName("처음 구독에 실패해도 다시 시도해 Redis에 연결되면 구독을 연다")
    void retriesUntilRedisIsReachable() throws Exception {
        AtomicInteger failuresLeft = new AtomicInteger(2);
        // 구독 컨테이너가 비동기 구독을 고르도록 Lettuce 팩토리를 상속해, 처음 두 번만 연결을 실패시킨다.
        LettuceConnectionFactory flaky =
                new LettuceConnectionFactory(
                        SharedContainers.REDIS.getHost(),
                        SharedContainers.REDIS.getMappedPort(SharedContainers.REDIS_PORT)) {
                    @Override
                    public RedisConnection getConnection() {
                        if (failuresLeft.getAndDecrement() > 0) {
                            throw new RedisConnectionFailureException("Redis is starting");
                        }
                        return super.getConnection();
                    }
                };
        flaky.afterPropertiesSet();
        flaky.start();
        RealtimeSignalSubscription subscription = subscription(flaky);
        try {
            subscription.start();

            await().atMost(Duration.ofSeconds(10)).until(subscription::isListening);
            assertThat(failuresLeft.get()).isNegative();
            assertThat(listeningGauge()).isEqualTo(1.0);
        } finally {
            subscription.stop();
            subscription.destroy();
            flaky.destroy();
        }
    }

    private RealtimeSignalSubscription subscription(RedisConnectionFactory connectionFactory) {
        return new RealtimeSignalSubscription(
                connectionFactory, subscriber, meters, Duration.ofMillis(100), Duration.ofSeconds(3));
    }

    private double listeningGauge() {
        return meters.get("realtime.subscription.listening").gauge().value();
    }
}
