package com.orbit.realtime.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.support.RedisAdapterTestSupport;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 같은 Redis 채널에 다른 테스트 컨텍스트의 신호가 섞일 수 있어, 이 테스트만 쓰는 식별자로 받은 메시지를 고른다. */
@DisplayName("Redis 실시간 신호 발행")
class RedisRealtimeSignalPublisherTest extends RedisAdapterTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T01:00:00Z");
    private static final long ORGANIZATION_ID = 987_101L;
    private static final long ACCOUNT_ID = 987_102L;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private RedisMessageListenerContainer container;

    @BeforeEach
    void subscribe() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.setTaskExecutor(new SyncTaskExecutor());
        container.addMessageListener(
                (message, pattern) -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(RedisRealtimeSignalPublisher.CHANNEL));
        container.afterPropertiesSet();
        container.start();
    }

    @AfterEach
    void unsubscribe() throws Exception {
        container.stop();
        container.destroy();
    }

    @Test
    @DisplayName("채널 하나에 종류와 대상·시각만 담은 JSON으로 보낸다")
    void publishesSignalsAsJsonOnOneChannel() {
        RedisRealtimeSignalPublisher publisher = new RedisRealtimeSignalPublisher(redisTemplate, jsonMapper, meters);
        await().atMost(Duration.ofSeconds(5)).until(container::isListening);

        publisher.publish(new RealtimeSignal.OrganizationWorksChanged(ORGANIZATION_ID, List.of(10L, 11L), OCCURRED_AT));
        publisher.publish(new RealtimeSignal.AccountNotificationCreated(ACCOUNT_ID, OCCURRED_AT));
        publisher.publish(new RealtimeSignal.AccountNotificationRead(ACCOUNT_ID, OCCURRED_AT));

        JsonNode works = await().atMost(Duration.ofSeconds(5)).until(() -> find("work-changed"), node -> node != null);
        JsonNode created =
                await().atMost(Duration.ofSeconds(5)).until(() -> find("notification-created"), node -> node != null);
        JsonNode read =
                await().atMost(Duration.ofSeconds(5)).until(() -> find("notification-read"), node -> node != null);
        assertThat(works.path("organizationId").asLong()).isEqualTo(ORGANIZATION_ID);
        assertThat(works.path("workIds").toString()).isEqualTo("[10,11]");
        assertThat(jsonMapper.convertValue(works.path("occurredAt"), Instant.class))
                .isEqualTo(OCCURRED_AT);
        assertThat(created.path("accountId").asLong()).isEqualTo(ACCOUNT_ID);
        assertThat(read.path("accountId").asLong()).isEqualTo(ACCOUNT_ID);
        assertThat(meters.get("realtime.signals.published")
                        .tag("result", "success")
                        .counters())
                .hasSize(3);
    }

    /** 이 테스트가 보낸 그 종류의 메시지. 아직 받지 않았으면 null. */
    private JsonNode find(String type) {
        return received.stream()
                .map(jsonMapper::readTree)
                .filter(node -> node.path("type").asString().equals(type))
                .filter(node -> node.path("organizationId").toString().equals(Long.toString(ORGANIZATION_ID))
                        || node.path("accountId").toString().equals(Long.toString(ACCOUNT_ID)))
                .findFirst()
                .orElse(null);
    }
}
