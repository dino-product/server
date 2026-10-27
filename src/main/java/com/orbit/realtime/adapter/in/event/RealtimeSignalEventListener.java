package com.orbit.realtime.adapter.in.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.notification.NotificationCreated;
import com.orbit.notification.NotificationRead;
import com.orbit.realtime.application.port.in.command.PublishRealtimeSignalUseCase;
import com.orbit.realtime.domain.RealtimeSignal;
import com.orbit.schedule.WorkChanged;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * schedule·notification이 저장한 변경을 커밋 뒤 실시간 신호로 알린다. 일반 이벤트 리스너로 발행 트랜잭션 안에서 받아 커밋 뒤 동작을 걸고, 커밋되면 신호 전용
 * 실행기에 보내기를 맡긴다. 롤백되면 보내지 않는다.
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}를 쓰지 않는다. Spring Modulith가 그런 리스너를 모두 감싸 실행 전에 별도 트랜잭션으로 발행
 *       상태를 갱신하므로(ADR-004), 기록하지 않는 신호마다 DB 연결을 쓰고 DB 장애 때 신호가 멈추기 때문이다. 신호는 이벤트 발행 기록 대상도 아니다.
 *   <li>발행 트랜잭션 안에서 동기로 돌므로 어떤 예외도 밖으로 던지지 않는다. 던지면 발행한 쪽의 저장이 롤백된다. 신호를 만들지 못하거나 맡기지 못하면 버리고
 *       {@code realtime.signals.dropped}(사유별)로 센다.
 *   <li>보내기는 한 스레드가 차례로 맡아 발행 순서를 지키고, Redis가 느리거나 멈춰도 요청 스레드와 다른 모듈 리스너의 실행기를 붙잡지 않는다. 줄(1,000개)이
 *       차면 새 신호를, 줄에서 30초보다 오래 기다린 신호는 보내지 않고 버린다. 늦게 보낸 신호는 의미가 없고 Redis가 돌아온 뒤 한꺼번에 몰리지 않게 하기
 *       위해서다.
 *   <li>실행기는 시작할 때마다 새로 만든다. 웹 서버의 정상 종료가 끝난 뒤(그동안 커밋된 신호까지) 줄을 비우고, Redis 연결보다 먼저 멈춘다.
 * </ul>
 */
@Slf4j
@Component
class RealtimeSignalEventListener implements SmartLifecycle {

    /** 웹 서버 정상 종료(DEFAULT_PHASE - 1024)·웹 서버 정지(DEFAULT_PHASE - 2048)보다 뒤에, Redis 연결(0)보다 먼저 멈춘다. */
    static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

    private static final int QUEUE_CAPACITY = 1_000;
    private static final Duration STALE_AFTER = Duration.ofSeconds(30);
    private static final long SHUTDOWN_WAIT_MILLIS = 5_000;

    private final PublishRealtimeSignalUseCase publishUseCase;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Supplier<Executor> executorFactory;
    private volatile Executor executor;

    @Autowired
    RealtimeSignalEventListener(PublishRealtimeSignalUseCase publishUseCase, MeterRegistry meters, Clock clock) {
        this(
                publishUseCase,
                meters,
                clock,
                () -> new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                        Thread.ofPlatform().name("realtime-signal").daemon(true).factory()));
    }

    RealtimeSignalEventListener(
            PublishRealtimeSignalUseCase publishUseCase,
            MeterRegistry meters,
            Clock clock,
            Supplier<Executor> executorFactory) {
        this.publishUseCase = publishUseCase;
        this.meters = meters;
        this.clock = clock;
        this.executorFactory = executorFactory;
    }

    @EventListener
    void on(WorkChanged event) {
        signal(() -> new RealtimeSignal.OrganizationWorksChanged(
                event.organizationId(), event.workIds(), event.occurredAt()));
    }

    @EventListener
    void on(NotificationCreated event) {
        signal(() -> new RealtimeSignal.AccountNotificationCreated(event.accountId(), event.occurredAt()));
    }

    @EventListener
    void on(NotificationRead event) {
        signal(() -> new RealtimeSignal.AccountNotificationRead(event.accountId(), event.occurredAt()));
    }

    @Override
    public void start() {
        executor = executorFactory.get();
    }

    @Override
    public void stop() {
        Executor current = executor;
        executor = null;
        if (current instanceof ExecutorService service) {
            service.shutdown();
            try {
                if (!service.awaitTermination(SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    service.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                service.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    private void signal(Supplier<RealtimeSignal> signal) {
        try {
            afterCommit(signal.get());
        } catch (RuntimeException exception) {
            log.warn("Realtime signal could not be prepared", exception);
            dropped("unknown", "invalid");
        }
    }

    private void afterCommit(RealtimeSignal signal) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 트랜잭션 밖에서 발행했으면 변경이 이미 반영됐다.
            submit(signal);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(signal);
            }
        });
    }

    /** 커밋 뒤 동작에서 던지면 이미 끝난 커밋이 호출자에게 실패로 보이므로 던지지 않는다. */
    private void submit(RealtimeSignal signal) {
        String type = signal.message().name();
        Executor current = executor;
        if (current == null) {
            dropped(type, "stopped");
            return;
        }
        Instant queuedAt = clock.instant();
        try {
            current.execute(() -> publish(signal, queuedAt));
        } catch (RejectedExecutionException exception) {
            log.debug("Realtime signal dropped: type={}", type, exception);
            dropped(type, "queue_full");
        }
    }

    private void publish(RealtimeSignal signal, Instant queuedAt) {
        if (clock.instant().isAfter(queuedAt.plus(STALE_AFTER))) {
            dropped(signal.message().name(), "stale");
            return;
        }
        publishUseCase.publish(signal);
    }

    private void dropped(String type, String reason) {
        meters.counter("realtime.signals.dropped", "type", type, "reason", reason)
                .increment();
    }
}
