package com.orbit.realtime.adapter.out.connection;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.orbit.realtime.application.port.in.command.RealtimeChannel;
import com.orbit.realtime.application.port.out.RealtimeConnectionPort;
import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.domain.RealtimeMessage;
import com.orbit.realtime.domain.RealtimeMessage.Closed;
import com.orbit.realtime.domain.RealtimeMessage.Connected;
import com.orbit.realtime.domain.RealtimeMessage.Reconnect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 인스턴스 메모리의 실시간 연결 목록과 연결마다의 전송.
 *
 * <ul>
 *   <li><b>상한</b>: 인스턴스 전체는 세마포어 허가 수로 지키고, 계정별은 등록할 때 그 계정의 가장 오래된 연결에 {@code closed}(replaced)를 보내고 닫아
 *       지킨다. 허가는 연결을 목록에서 뺄 때 한 번만 돌려준다.
 *   <li><b>전송</b>: 연결마다 보낼 것을 줄 세워 한 번에 하나씩 전송 스레드에서 쓴다. 쓰기에 실패하거나 줄이 너무 길어지면 닫는다. 쓰기 하나가 쓰기 한도보다 오래
 *       막히면 목록에서 빼고 닫기를 줄 세운다. 막힌 쓰기가 연결의 잠금을 쥐고 있어 다른 스레드가 닫으면 그 스레드도 막히므로, 막힌 쓰기가 끝난 뒤 전송 작업이
 *       닫는다(쓰기가 실패하면 실패로 닫는다).
 *   <li><b>주기 작업</b>: 유지 신호 간격마다 수명이 지난 연결에 {@code reconnect}를 보내고 닫으며, 나머지 중 보낼 것이 없는 연결에 유지 신호를 보낸다. 같은
 *       때 돌려받지 않은 허가 수와 목록 크기를 맞춰 본다. 막힌 쓰기는 1초마다 확인한다.
 *   <li><b>종료</b>: 기본 phase라 웹 서버의 정상 종료보다 먼저 멈춘다. 새 연결을 받지 않고 모든 연결에 {@code reconnect}를 보낸 뒤 닫아, 화면이 정상 종료를
 *       기다리지 않고 다른 인스턴스로 다시 연결하게 한다. 실행기는 시작할 때마다 새로 만들고 멈출 때 정리하므로, 멈춘 뒤(컨텍스트 일시정지 등) 다시 시작할 수 있다.
 * </ul>
 *
 * <p>지표(이름 앞은 모두 {@code realtime.connections}): 그 자체는 화면 종류별 열린 연결 수, {@code .opened}는 연 연결 수, {@code .closed}는 닫은
 * 사유별 수, {@code .rejected}는 거부 사유별 수, {@code .drift}는 허가 수와 목록 크기가 어긋난 횟수다.
 */
@Slf4j
@Component
class InMemoryRealtimeConnections implements RealtimeConnectionPort, SmartLifecycle {

    private static final int OUTBOX_LIMIT = 100;
    private static final long STALL_CHECK_MILLIS = 1_000;
    private static final long SHUTDOWN_WAIT_MILLIS = 5_000;

    private final RealtimeConnectionProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Supplier<Executor> deliveryFactory;
    private final Supplier<ScheduledExecutorService> tickerFactory;
    private final Semaphore permits;
    private final AtomicLong connectionIds = new AtomicLong();
    private final Map<Long, Connection> byId = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Connection>> byAccount = new ConcurrentHashMap<>();
    private final Map<ClientType, AtomicInteger> openByClient = new EnumMap<>(ClientType.class);
    private final Counter drifts;
    private volatile Executor delivery;
    private volatile ScheduledExecutorService ticker;
    private volatile boolean running;
    private int lastDrift;

    @Autowired
    InMemoryRealtimeConnections(RealtimeConnectionProperties properties, MeterRegistry meters, Clock clock) {
        this(
                properties,
                meters,
                clock,
                () -> new ThreadPoolExecutor(
                        properties.deliveryThreads(),
                        properties.deliveryThreads(),
                        0,
                        TimeUnit.MILLISECONDS,
                        // 연결마다 전송 작업은 하나만 줄에 서므로, 닫는 중인 연결까지 담도록 상한의 두 배를 둔다.
                        new LinkedBlockingQueue<>(properties.maxConnections() * 2),
                        Thread.ofPlatform()
                                .name("realtime-delivery-", 0)
                                .daemon(true)
                                .factory()),
                () -> Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().name("realtime-ticker").daemon(true).factory()));
    }

    /**
     * 실행기는 시작할 때마다 공급자에게서 새로 받는다. 주기 작업 실행기 공급자가 없으면(테스트) 주기 작업을 예약하지 않으며, 호출하는 쪽이 {@link #heartbeat()}·
     * {@link #removeStalled()}를 직접 부른다.
     */
    InMemoryRealtimeConnections(
            RealtimeConnectionProperties properties,
            MeterRegistry meters,
            Clock clock,
            Supplier<Executor> deliveryFactory,
            Supplier<ScheduledExecutorService> tickerFactory) {
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        this.deliveryFactory = deliveryFactory;
        this.tickerFactory = tickerFactory;
        this.permits = new Semaphore(properties.maxConnections());
        for (ClientType clientType : ClientType.values()) {
            AtomicInteger open = new AtomicInteger();
            openByClient.put(clientType, open);
            Gauge.builder("realtime.connections", open, AtomicInteger::get)
                    .description("이 인스턴스에 열린 실시간 연결 수")
                    .tag("client", clientType.tag())
                    .register(meters);
        }
        this.drifts = Counter.builder("realtime.connections.drift")
                .description("돌려받지 않은 허가 수와 연결 목록 크기가 두 번 연달아 어긋난 횟수")
                .register(meters);
    }

    @Override
    public boolean register(Long accountId, Long organizationId, ClientType clientType, RealtimeChannel channel) {
        if (!running) {
            reject("shutting_down");
            return false;
        }
        if (!permits.tryAcquire()) {
            reject("capacity");
            return false;
        }
        Connection connection = new Connection(
                connectionIds.incrementAndGet(), accountId, organizationId, clientType, channel, expiresAt());
        List<Connection> replaced = new ArrayList<>();
        // 종료 처리는 byId를 돌며 닫으므로, 계정 목록에 먼저 넣어 닫힌 연결이 계정 목록에만 남지 않게 한다.
        byAccount.compute(accountId, (id, connections) -> {
            Deque<Connection> list = connections == null ? new ArrayDeque<>() : connections;
            list.addLast(connection);
            while (list.size() > properties.maxConnectionsPerAccount()) {
                replaced.add(list.pollFirst());
            }
            return list;
        });
        byId.put(connection.id, connection);
        // 계정 목록에 넣은 뒤 byId에 넣기 전에 다른 등록이 이 연결을 밀어내 닫았으면 byId에서 지우지 못했으므로 여기서 지운다.
        if (!connection.registered.get()) {
            byId.remove(connection.id, connection);
        }
        openByClient.get(clientType).incrementAndGet();
        meters.counter("realtime.connections.opened", "client", clientType.tag())
                .increment();

        channel.onClosed(() -> close(connection, CloseReason.CHANNEL_CLOSED, null));
        enqueue(connection, new Send(new Connected()));
        replaced.forEach(old -> close(old, CloseReason.REPLACED, new Closed(Closed.REPLACED)));
        // 종료가 시작된 뒤 목록에 들어왔으면 종료 처리가 이 연결을 보지 못했을 수 있다.
        if (!running) {
            close(connection, CloseReason.SHUTDOWN, new Reconnect());
        }
        // 종료와 겹쳤거나 전송 작업을 맡기지 못해 이미 닫았으면 화면이 다른 인스턴스로 다시 연결하도록 거부로 알린다.
        return connection.registered.get();
    }

    /** 연결 수가 상한(기본 2,000)으로 묶여 있고 신호가 드물어, 따로 색인을 두지 않고 목록을 훑어 고른다. */
    @Override
    public void sendToOrganization(Long organizationId, RealtimeMessage message) {
        for (Connection connection : byId.values()) {
            if (connection.organizationId.equals(organizationId)) {
                enqueue(connection, new Send(message));
            }
        }
    }

    @Override
    public void sendToAccount(Long accountId, RealtimeMessage message) {
        for (Connection connection : byId.values()) {
            if (connection.accountId.equals(accountId)) {
                enqueue(connection, new Send(message));
            }
        }
    }

    /** 수명이 지난 연결에 {@code reconnect}를 보내고 닫고, 나머지 중 보낼 것이 없는 연결에 유지 신호를 보낸다. 이어서 허가 수와 목록 크기를 맞춰 본다. */
    void heartbeat() {
        Instant now = clock.instant();
        for (Connection connection : byId.values()) {
            if (!connection.expiresAt.isAfter(now)) {
                close(connection, CloseReason.LIFETIME, new Reconnect());
            } else if (connection.isIdle()) {
                enqueue(connection, new Heartbeat());
            }
        }
        checkDrift();
    }

    /** 쓰기 하나가 쓰기 한도보다 오래 막힌 연결을 목록에서 빼고 닫기를 줄 세운다. 막힌 쓰기가 끝나면 전송 작업이 이어서 닫는다. */
    void removeStalled() {
        Instant deadline = clock.instant().minus(properties.writeTimeout());
        for (Connection connection : byId.values()) {
            Instant writingSince = connection.writingSince;
            if (writingSince != null
                    && writingSince.isBefore(deadline)
                    && unregister(connection, CloseReason.WRITE_TIMEOUT)) {
                connection.clearPending();
                connection.push(new Complete());
                schedule(connection);
            }
        }
    }

    @Override
    public void start() {
        delivery = deliveryFactory.get();
        ticker = tickerFactory == null ? null : tickerFactory.get();
        running = true;
        if (ticker != null) {
            long heartbeatMillis = properties.heartbeatInterval().toMillis();
            ticker.scheduleWithFixedDelay(
                    guarded("heartbeat", this::heartbeat), heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
            ticker.scheduleWithFixedDelay(
                    guarded("stall check", this::removeStalled),
                    STALL_CHECK_MILLIS,
                    STALL_CHECK_MILLIS,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (ticker != null) {
            ticker.shutdownNow();
        }
        byId.values().forEach(connection -> close(connection, CloseReason.SHUTDOWN, new Reconnect()));
        if (delivery instanceof ExecutorService executor) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private Instant expiresAt() {
        Duration lifetime = properties.connectionLifetime();
        long earlierMillis = ThreadLocalRandom.current().nextLong(lifetime.toMillis() / 10 + 1);
        return clock.instant().plus(lifetime).minusMillis(earlierMillis);
    }

    private void enqueue(Connection connection, Delivery delivery) {
        if (!connection.registered.get()) {
            return;
        }
        if (!connection.offer(delivery)) {
            close(connection, CloseReason.OUTBOX_FULL, null);
            return;
        }
        schedule(connection);
    }

    /** 목록에서 빼고 허가를 돌려준 뒤, 보낼 것을 비우고 마지막 이벤트(있으면)와 닫기를 줄 세운다. 이미 뺀 연결이면 아무것도 하지 않는다. */
    private void close(Connection connection, CloseReason reason, RealtimeMessage farewell) {
        if (!unregister(connection, reason)) {
            return;
        }
        connection.clearPending();
        if (farewell != null) {
            connection.push(new Send(farewell));
        }
        connection.push(new Complete());
        schedule(connection);
    }

    private boolean unregister(Connection connection, CloseReason reason) {
        if (!connection.registered.compareAndSet(true, false)) {
            return false;
        }
        byId.remove(connection.id);
        byAccount.computeIfPresent(connection.accountId, (id, connections) -> {
            connections.remove(connection);
            return connections.isEmpty() ? null : connections;
        });
        openByClient.get(connection.clientType).decrementAndGet();
        permits.release();
        meters.counter("realtime.connections.closed", "reason", reason.tag).increment();
        return true;
    }

    private void schedule(Connection connection) {
        if (!connection.draining.compareAndSet(false, true)) {
            return;
        }
        try {
            delivery.execute(() -> drain(connection));
        } catch (RejectedExecutionException exception) {
            // 이 연결의 전송 작업이 없으므로 닫기가 막히지 않는다.
            connection.draining.set(false);
            unregister(connection, CloseReason.DELIVERY_REJECTED);
            connection.clearPending();
            connection.channel.close();
        }
    }

    private void drain(Connection connection) {
        try {
            Delivery next;
            while ((next = connection.poll()) != null) {
                if (next instanceof Complete) {
                    connection.clearPending();
                    connection.channel.close();
                    return;
                }
                if (!write(connection, next)) {
                    return;
                }
            }
        } finally {
            connection.draining.set(false);
        }
        // 줄이 빈 것을 본 뒤 전송 작업을 끝내기 전에 들어온 것을 놓치지 않는다.
        if (!connection.isIdle()) {
            schedule(connection);
        }
    }

    private boolean write(Connection connection, Delivery delivery) {
        connection.writingSince = clock.instant();
        try {
            if (delivery instanceof Send send) {
                connection.channel.send(send.message());
            } else {
                connection.channel.sendHeartbeat();
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            log.debug("Realtime connection write failed: connectionId={}", connection.id, exception);
            unregister(connection, CloseReason.WRITE_FAILED);
            connection.clearPending();
            connection.channel.close();
            return false;
        } finally {
            connection.writingSince = null;
        }
    }

    /** 등록·해제 도중에는 한 번 어긋날 수 있어, 같은 차이가 두 번 연달아 보일 때만 센다. 주기 작업 스레드에서만 부른다. */
    private void checkDrift() {
        int drift = (properties.maxConnections() - permits.availablePermits()) - byId.size();
        if (drift != 0 && drift == lastDrift) {
            log.warn("Realtime connection permits drifted from registered connections: drift={}", drift);
            drifts.increment();
        }
        lastDrift = drift;
    }

    private void reject(String reason) {
        meters.counter("realtime.connections.rejected", "reason", reason).increment();
    }

    private static Runnable guarded(String name, Runnable task) {
        // 예외가 나면 예약 실행기가 이후 실행을 멈추므로 여기서 받는다.
        return () -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                log.error("Realtime connection {} failed", name, exception);
            }
        };
    }

    private enum CloseReason {
        CHANNEL_CLOSED("channel_closed"),
        REPLACED("replaced"),
        LIFETIME("lifetime"),
        WRITE_FAILED("write_failed"),
        WRITE_TIMEOUT("write_timeout"),
        OUTBOX_FULL("outbox_full"),
        DELIVERY_REJECTED("delivery_rejected"),
        SHUTDOWN("shutdown");

        private final String tag;

        CloseReason(String tag) {
            this.tag = tag;
        }
    }

    private sealed interface Delivery permits Send, Heartbeat, Complete {}

    private record Send(RealtimeMessage message) implements Delivery {}

    private record Heartbeat() implements Delivery {}

    private record Complete() implements Delivery {}

    private static final class Connection {

        private final long id;
        private final Long accountId;
        private final Long organizationId;
        private final ClientType clientType;
        private final RealtimeChannel channel;
        private final Instant expiresAt;
        private final AtomicBoolean registered = new AtomicBoolean(true);
        private final AtomicBoolean draining = new AtomicBoolean();
        private final Queue<Delivery> outbox = new ConcurrentLinkedQueue<>();
        private final AtomicInteger queued = new AtomicInteger();
        private volatile Instant writingSince;

        private Connection(
                long id,
                Long accountId,
                Long organizationId,
                ClientType clientType,
                RealtimeChannel channel,
                Instant expiresAt) {
            this.id = id;
            this.accountId = accountId;
            this.organizationId = organizationId;
            this.clientType = clientType;
            this.channel = channel;
            this.expiresAt = expiresAt;
        }

        /** 줄이 상한에 닿았으면 넣지 않는다. */
        private boolean offer(Delivery delivery) {
            if (queued.incrementAndGet() > OUTBOX_LIMIT) {
                queued.decrementAndGet();
                return false;
            }
            outbox.add(delivery);
            return true;
        }

        private void push(Delivery delivery) {
            queued.incrementAndGet();
            outbox.add(delivery);
        }

        private Delivery poll() {
            Delivery next = outbox.poll();
            if (next != null) {
                queued.decrementAndGet();
            }
            return next;
        }

        private void clearPending() {
            while (poll() != null) {
                // 비운다
            }
        }

        private boolean isIdle() {
            return outbox.isEmpty();
        }
    }
}
