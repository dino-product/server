package com.orbit.realtime.adapter.in.redis;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.orbit.realtime.application.port.in.command.DeliverRealtimeSignalUseCase;
import com.orbit.realtime.domain.RealtimeSignal;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 채널({@value #CHANNEL})에서 받은 신호를 이 인스턴스의 연결로 보낸다. 채널과 형식은 보내는 {@code RedisRealtimeSignalPublisher}와 맞춘다. 읽을 수 없는
 * 신호는 버리고 로그와 {@code realtime.signals.received}(result=invalid)만 남긴다.
 */
@Slf4j
@Component
class RedisRealtimeSignalSubscriber implements MessageListener {

    static final String CHANNEL = "orbit:realtime";

    private final DeliverRealtimeSignalUseCase deliverUseCase;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    RedisRealtimeSignalSubscriber(
            DeliverRealtimeSignalUseCase deliverUseCase, ObjectMapper objectMapper, MeterRegistry meters) {
        this.deliverUseCase = deliverUseCase;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RealtimeSignal signal;
        try {
            signal = objectMapper
                    .readValue(message.getBody(), ReceivedSignal.class)
                    .toSignal();
        } catch (RuntimeException exception) {
            log.warn("Unreadable realtime signal dropped", exception);
            meters.counter("realtime.signals.received", "type", "unknown", "result", "invalid")
                    .increment();
            return;
        }
        deliverUseCase.deliver(signal);
        meters.counter("realtime.signals.received", "type", signal.message().name(), "result", "delivered")
                .increment();
    }

    /** 채널에서 받는 형식. {@code type}은 화면 이벤트 이름과 같고 종류마다 쓰는 필드만 읽는다. 모르는 종류나 빠진 필드는 읽을 수 없는 신호다. */
    record ReceivedSignal(String type, Long organizationId, Long accountId, List<Long> workIds, Instant occurredAt) {

        RealtimeSignal toSignal() {
            if (type == null) {
                throw new IllegalArgumentException("realtime signal type is missing");
            }
            return switch (type) {
                case "work-changed" -> new RealtimeSignal.OrganizationWorksChanged(organizationId, workIds, occurredAt);
                case "notification-created" -> new RealtimeSignal.AccountNotificationCreated(accountId, occurredAt);
                case "notification-read" -> new RealtimeSignal.AccountNotificationRead(accountId, occurredAt);
                default -> throw new IllegalArgumentException("unknown realtime signal type: " + type);
            };
        }
    }
}
