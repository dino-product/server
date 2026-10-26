package com.orbit.realtime.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.notification.NotificationCreated;
import com.orbit.notification.NotificationRead;
import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.schedule.WorkChanged;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 트랜잭션 동기화를 직접 열고 커밋·롤백 뒤 동작을 불러 본다. 보내기 실행기는 부른 스레드에서 바로 실행하거나, 맡은 작업을 쥐고 있다가 테스트가 실행한다. */
@DisplayName("커밋된 변경의 실시간 신호")
class RealtimeSignalEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-26T01:00:00Z");

    private final List<RealtimeSignal> published = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AdjustableClock clock = new AdjustableClock(OCCURRED_AT);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서 받은 변경은 커밋된 뒤에야 신호로 보낸다")
    void publishesOnlyAfterCommit() {
        RealtimeSignalEventListener listener = started(Runnable::run);
        TransactionSynchronizationManager.initSynchronization();

        listener.on(new WorkChanged(1L, List.of(10L), OCCURRED_AT));
        listener.on(new NotificationCreated(7L, OCCURRED_AT));
        listener.on(new NotificationRead(7L, OCCURRED_AT));

        assertThat(published).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(published)
                .containsExactly(
                        new RealtimeSignal.OrganizationWorksChanged(1L, List.of(10L), OCCURRED_AT),
                        new RealtimeSignal.AccountNotificationCreated(7L, OCCURRED_AT),
                        new RealtimeSignal.AccountNotificationRead(7L, OCCURRED_AT));
    }

    @Test
    @DisplayName("롤백되면 신호를 보내지 않는다")
    void publishesNothingOnRollback() {
        RealtimeSignalEventListener listener = started(Runnable::run);
        TransactionSynchronizationManager.initSynchronization();

        listener.on(new WorkChanged(1L, List.of(10L), OCCURRED_AT));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 받은 변경은 바로 신호로 보낸다")
    void publishesImmediatelyOutsideTransaction() {
        RealtimeSignalEventListener listener = started(Runnable::run);

        listener.on(new WorkChanged(1L, List.of(10L), OCCURRED_AT));

        assertThat(published)
                .containsExactly(new RealtimeSignal.OrganizationWorksChanged(1L, List.of(10L), OCCURRED_AT));
    }

    @Test
    @DisplayName("신호를 만들지 못해도 발행한 쪽으로 예외를 던지지 않고 버린다")
    void dropsInvalidEventWithoutThrowing() {
        RealtimeSignalEventListener listener = started(Runnable::run);

        listener.on(new WorkChanged(null, List.of(10L), OCCURRED_AT));

        assertThat(published).isEmpty();
        assertThat(dropped("unknown", "invalid")).isEqualTo(1);
    }

    @Test
    @DisplayName("보내기 줄이 차거나 멈춘 뒤에는 신호를 버리고 센다")
    void dropsSignalWhenQueueIsFullOrStopped() {
        RealtimeSignalEventListener full = started(task -> {
            throw new RejectedExecutionException("full");
        });

        full.on(new NotificationRead(7L, OCCURRED_AT));
        full.stop();
        full.on(new NotificationRead(7L, OCCURRED_AT));

        assertThat(published).isEmpty();
        assertThat(dropped("notification-read", "queue_full")).isEqualTo(1);
        assertThat(dropped("notification-read", "stopped")).isEqualTo(1);
    }

    @Test
    @DisplayName("줄에서 30초보다 오래 기다린 신호는 보내지 않는다")
    void dropsStaleSignals() {
        List<Runnable> held = new ArrayList<>();
        RealtimeSignalEventListener listener = started(held::add);
        listener.on(new WorkChanged(1L, List.of(10L), OCCURRED_AT));
        listener.on(new WorkChanged(1L, List.of(11L), OCCURRED_AT));

        held.getFirst().run();
        clock.advance(Duration.ofSeconds(31));
        held.getLast().run();

        assertThat(published)
                .containsExactly(new RealtimeSignal.OrganizationWorksChanged(1L, List.of(10L), OCCURRED_AT));
        assertThat(dropped("work-changed", "stale")).isEqualTo(1);
    }

    private RealtimeSignalEventListener started(Executor executor) {
        RealtimeSignalEventListener listener =
                new RealtimeSignalEventListener(published::add, meters, clock, () -> executor);
        listener.start();
        return listener;
    }

    private double dropped(String type, String reason) {
        var counter = meters.find("realtime.signals.dropped")
                .tag("type", type)
                .tag("reason", reason)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static final class AdjustableClock extends Clock {

        private volatile Instant instant;

        private AdjustableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
