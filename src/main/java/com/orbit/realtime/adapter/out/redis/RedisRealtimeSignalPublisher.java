package com.orbit.realtime.adapter.out.redis;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.orbit.realtime.application.port.out.RealtimeSignalPublisherPort;
import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.realtime.domain.RealtimeSignal.AccountNotificationCreated;
import com.orbit.realtime.domain.RealtimeSignal.AccountNotificationRead;
import com.orbit.realtime.domain.RealtimeSignal.OrganizationWorksChanged;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 신호를 Redis 채널 하나({@value #CHANNEL})에 JSON으로 보낸다. 채널과 형식은 구독하는 {@code RedisRealtimeSignalSubscriber}와 맞춘다. Redis
 * Pub/Sub은 저장하지 않아 그때 구독 중인 인스턴스만 받는다. 보내지 못하면 다시 보내지 않고 로그와 {@code realtime.signals.published}
 * (result=failure)만 남긴다.
 */
@Slf4j
@Component
class RedisRealtimeSignalPublisher implements RealtimeSignalPublisherPort {

    static final String CHANNEL = "orbit:realtime";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    RedisRealtimeSignalPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meters) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    @Override
    public void publish(RealtimeSignal signal) {
        String type = signal.message().name();
        String result = "success";
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(SignalMessage.from(signal)));
        } catch (RuntimeException exception) {
            result = "failure";
            log.warn("Realtime signal publish failed: type={}", type, exception);
        }
        meters.counter("realtime.signals.published", "type", type, "result", result)
                .increment();
    }

    /** 채널로 보내는 형식. {@code type}은 화면 이벤트 이름과 같고, 받는 쪽은 그 종류에 쓰는 필드만 읽는다. */
    record SignalMessage(String type, Long organizationId, Long accountId, List<Long> workIds, Instant occurredAt) {

        static SignalMessage from(RealtimeSignal signal) {
            String type = signal.message().name();
            return switch (signal) {
                case OrganizationWorksChanged works ->
                    new SignalMessage(type, works.organizationId(), null, works.workIds(), works.occurredAt());
                case AccountNotificationCreated created ->
                    new SignalMessage(type, null, created.accountId(), null, created.occurredAt());
                case AccountNotificationRead read ->
                    new SignalMessage(type, null, read.accountId(), null, read.occurredAt());
            };
        }
    }
}
