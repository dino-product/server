package com.orbit.realtime.adapter.in.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import com.orbit.realtime.domain.RealtimeSignal;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Redis 실시간 신호 구독")
class RedisRealtimeSignalSubscriberTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T01:00:00Z");

    private final List<RealtimeSignal> delivered = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisRealtimeSignalSubscriber subscriber = new RedisRealtimeSignalSubscriber(
            delivered::add, JsonMapper.builder().build(), meters);

    @Test
    @DisplayName("종류마다 쓰는 필드로 신호를 만들어 연결로 넘긴다")
    void deliversEachSignalType() {
        receive("""
                {"type":"work-changed","organizationId":1,"accountId":null,"workIds":[10,11],
                 "occurredAt":"2026-10-26T01:00:00Z"}
                """);
        receive("""
                {"type":"notification-created","organizationId":null,"accountId":7,"workIds":null,
                 "occurredAt":"2026-10-26T01:00:00Z"}
                """);
        receive("""
                {"type":"notification-read","accountId":7,"occurredAt":"2026-10-26T01:00:00Z"}
                """);

        assertThat(delivered)
                .containsExactly(
                        new RealtimeSignal.OrganizationWorksChanged(1L, List.of(10L, 11L), OCCURRED_AT),
                        new RealtimeSignal.AccountNotificationCreated(7L, OCCURRED_AT),
                        new RealtimeSignal.AccountNotificationRead(7L, OCCURRED_AT));
    }

    @Test
    @DisplayName("읽을 수 없는 신호(JSON 아님, 모르는 종류, 빠진 필드)는 버리고 센다")
    void dropsUnreadableSignals() {
        receive("not json");
        receive("""
                {"type":"work-deleted","organizationId":1,"workIds":[10],"occurredAt":"2026-10-26T01:00:00Z"}
                """);
        receive("""
                {"type":"work-changed","workIds":[10],"occurredAt":"2026-10-26T01:00:00Z"}
                """);
        receive("""
                {"accountId":7,"occurredAt":"2026-10-26T01:00:00Z"}
                """);

        assertThat(delivered).isEmpty();
        assertThat(meters.get("realtime.signals.received")
                        .tag("result", "invalid")
                        .counter()
                        .count())
                .isEqualTo(4);
    }

    private void receive(String body) {
        subscriber.onMessage(
                new DefaultMessage(
                        RedisRealtimeSignalSubscriber.CHANNEL.getBytes(StandardCharsets.UTF_8),
                        body.getBytes(StandardCharsets.UTF_8)),
                null);
    }
}
