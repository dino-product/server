package com.orbit.realtime.adapter.out.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.realtime.domain.ClientType;
import com.orbit.realtime.domain.RealtimeMessage.Closed;
import com.orbit.realtime.domain.RealtimeMessage.Connected;
import com.orbit.realtime.domain.RealtimeMessage.NotificationRead;
import com.orbit.realtime.domain.RealtimeMessage.Reconnect;
import com.orbit.realtime.domain.RealtimeMessage.WorkChanged;
import com.orbit.realtime.fake.RecordingRealtimeChannel;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 주기 작업은 예약하지 않고 테스트가 직접 부른다. 전송은 막힌 쓰기와 재시작을 볼 때만 실제 스레드에서, 나머지는 부른 스레드에서 바로 한다.
 */
@DisplayName("인스턴스 실시간 연결 목록")
class InMemoryRealtimeConnectionsTest {

    private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");
    private static final Long ORGANIZATION_ID = 1L;

    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    @DisplayName("등록한 연결에 connected를 보내고 화면 종류별로 센다")
    void registersAndSendsConnected() {
        InMemoryRealtimeConnections connections = started(properties(10, 5));
        RecordingRealtimeChannel channel = new RecordingRealtimeChannel();

        assertThat(connections.register(1L, ORGANIZATION_ID, ClientType.MOBILE_WEB, channel))
                .isTrue();

        assertThat(channel.sent()).containsExactly(new Connected());
        assertThat(open(ClientType.MOBILE_WEB)).isEqualTo(1);
        assertThat(meters.get("realtime.connections.opened")
                        .tag("client", "mobile-web")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("인스턴스 상한에 닿으면 거부하고, 연결이 닫혀 허가가 돌아오면 다시 받는다")
    void rejectsWhenInstanceIsFullUntilAConnectionCloses() {
        InMemoryRealtimeConnections connections = started(properties(2, 5));
        RecordingRealtimeChannel first = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, first);
        connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel());

        assertThat(connections.register(3L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                .isFalse();
        assertThat(rejected("capacity")).isEqualTo(1);

        first.disconnect();

        assertThat(connections.register(3L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                .isTrue();
        assertThat(open(ClientType.DESKTOP_WEB)).isEqualTo(2);
        assertThat(closed("channel_closed")).isEqualTo(1);
    }

    @Test
    @DisplayName("계정 상한을 넘으면 그 계정의 가장 오래된 연결에 closed(replaced)를 보내고 닫는다")
    void replacesOldestConnectionOfAccount() {
        InMemoryRealtimeConnections connections = started(properties(10, 2));
        RecordingRealtimeChannel oldest = new RecordingRealtimeChannel();
        RecordingRealtimeChannel middle = new RecordingRealtimeChannel();
        RecordingRealtimeChannel other = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, oldest);
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, middle);
        connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, other);

        RecordingRealtimeChannel newest = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, newest);

        assertThat(oldest.sent()).containsExactly(new Connected(), new Closed(Closed.REPLACED));
        assertThat(oldest.isClosed()).isTrue();
        assertThat(middle.isClosed()).isFalse();
        assertThat(newest.isClosed()).isFalse();
        assertThat(other.isClosed()).isFalse();
        assertThat(open(ClientType.DESKTOP_WEB)).isEqualTo(3);
        assertThat(closed("replaced")).isEqualTo(1);
    }

    @Test
    @DisplayName("유지 신호 때 수명이 지난 연결은 reconnect 뒤 닫고, 나머지에는 유지 신호를 보낸다")
    void reconnectsExpiredConnectionsAndHeartbeatsOthers() {
        InMemoryRealtimeConnections connections = started(properties(10, 5));
        RecordingRealtimeChannel early = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, early);
        // 수명은 연결마다 최대 10% 앞당기므로 90% 전까지는 닫히지 않는다.
        clock.advance(Duration.ofMinutes(26));
        RecordingRealtimeChannel late = new RecordingRealtimeChannel();
        connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, late);

        connections.heartbeat();

        assertThat(early.heartbeats()).isEqualTo(1);
        assertThat(late.heartbeats()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(4));
        connections.heartbeat();

        assertThat(early.sent()).containsExactly(new Connected(), new Reconnect());
        assertThat(early.isClosed()).isTrue();
        assertThat(late.isClosed()).isFalse();
        assertThat(late.heartbeats()).isEqualTo(2);
        assertThat(closed("lifetime")).isEqualTo(1);
        assertThat(open(ClientType.DESKTOP_WEB)).isEqualTo(1);
    }

    @Test
    @DisplayName("쓰기에 실패한 연결은 닫고 허가를 돌려준다")
    void closesConnectionWhoseWriteFails() {
        InMemoryRealtimeConnections connections = started(properties(1, 5));
        RecordingRealtimeChannel channel = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, channel);
        channel.failWrites();

        connections.heartbeat();

        assertThat(channel.isClosed()).isTrue();
        assertThat(closed("write_failed")).isEqualTo(1);
        assertThat(connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                .isTrue();
    }

    @Test
    @DisplayName("쓰기 한도보다 오래 막힌 연결은 목록에서 빼고, 막힌 쓰기가 실패하면 닫는다")
    void removesStalledConnectionAndClosesItWhenTheWriteFails() throws Exception {
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        try {
            InMemoryRealtimeConnections connections = started(properties(1, 5), delivery);
            RecordingRealtimeChannel channel = new RecordingRealtimeChannel();
            CountDownLatch release = new CountDownLatch(1);
            channel.blockNextWrite(release, true);
            connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, channel);
            channel.awaitWriteStarted();

            clock.advance(Duration.ofSeconds(5));
            connections.removeStalled();

            assertThat(open(ClientType.DESKTOP_WEB)).isEqualTo(1);

            clock.advance(Duration.ofMillis(1));
            connections.removeStalled();

            assertThat(open(ClientType.DESKTOP_WEB)).isZero();
            assertThat(closed("write_timeout")).isEqualTo(1);
            assertThat(channel.isClosed()).isFalse();
            assertThat(connections.register(
                            2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                    .isTrue();

            release.countDown();

            await().atMost(Duration.ofSeconds(5)).until(channel::isClosed);
            assertThat(closed("write_failed")).isZero();
        } finally {
            delivery.shutdownNow();
        }
    }

    @Test
    @DisplayName("막혔던 쓰기가 늦게 성공해도 목록에서 뺀 연결은 그 쓰기가 끝난 뒤 닫는다")
    void closesStalledConnectionAfterTheWriteFinallySucceeds() throws Exception {
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        try {
            InMemoryRealtimeConnections connections = started(properties(1, 5), delivery);
            RecordingRealtimeChannel channel = new RecordingRealtimeChannel();
            CountDownLatch release = new CountDownLatch(1);
            channel.blockNextWrite(release, false);
            connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, channel);
            channel.awaitWriteStarted();

            clock.advance(Duration.ofSeconds(6));
            connections.removeStalled();
            release.countDown();

            await().atMost(Duration.ofSeconds(5)).until(channel::isClosed);
            assertThat(channel.sent()).containsExactly(new Connected());
            assertThat(closed("write_timeout")).isEqualTo(1);
        } finally {
            delivery.shutdownNow();
        }
    }

    @Test
    @DisplayName("전송 작업을 맡기지 못한 연결은 닫고 등록하지 않은 것으로 알린다")
    void reportsRejectionWhenDeliveryCannotBeScheduled() {
        InMemoryRealtimeConnections connections = started(properties(1, 5), task -> {
            throw new RejectedExecutionException("full");
        });
        RecordingRealtimeChannel channel = new RecordingRealtimeChannel();

        assertThat(connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, channel))
                .isFalse();
        assertThat(channel.isClosed()).isTrue();
        assertThat(closed("delivery_rejected")).isEqualTo(1);
        assertThat(open(ClientType.DESKTOP_WEB)).isZero();
    }

    @Test
    @DisplayName("멈춘 뒤 다시 시작하면 새 실행기로 연결을 다시 받는다")
    void restartsAfterStop() {
        InMemoryRealtimeConnections connections = new InMemoryRealtimeConnections(
                properties(10, 5),
                meters,
                clock,
                Executors::newSingleThreadExecutor,
                Executors::newSingleThreadScheduledExecutor);
        connections.start();
        RecordingRealtimeChannel before = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, before);
        await().atMost(Duration.ofSeconds(5)).until(() -> !before.sent().isEmpty());

        connections.stop();
        connections.start();
        RecordingRealtimeChannel after = new RecordingRealtimeChannel();

        try {
            assertThat(connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, after))
                    .isTrue();
            await().atMost(Duration.ofSeconds(5)).until(() -> after.sent().contains(new Connected()));
            assertThat(before.isClosed()).isTrue();
        } finally {
            connections.stop();
        }
    }

    @Test
    @DisplayName("작업 신호는 그 발주사의 연결에만, 알림 신호는 발주사와 관계없이 그 계정의 연결에만 보낸다")
    void sendsToOrganizationOrAccountConnections() {
        InMemoryRealtimeConnections connections = started(properties(10, 5));
        RecordingRealtimeChannel accountInFirst = new RecordingRealtimeChannel();
        RecordingRealtimeChannel accountInSecond = new RecordingRealtimeChannel();
        RecordingRealtimeChannel otherInFirst = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, accountInFirst);
        connections.register(1L, 2L, ClientType.DESKTOP_WEB, accountInSecond);
        connections.register(3L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, otherInFirst);
        WorkChanged workChanged = new WorkChanged(List.of(10L), NOW);
        NotificationRead read = new NotificationRead(NOW);

        connections.sendToOrganization(ORGANIZATION_ID, workChanged);
        connections.sendToAccount(1L, read);

        assertThat(accountInFirst.sent()).containsExactly(new Connected(), workChanged, read);
        assertThat(accountInSecond.sent()).containsExactly(new Connected(), read);
        assertThat(otherInFirst.sent()).containsExactly(new Connected(), workChanged);
    }

    @Test
    @DisplayName("보낼 것이 100개를 넘게 밀린 연결은 닫는다")
    void closesConnectionWhoseOutboxOverflows() {
        List<Runnable> held = new ArrayList<>();
        InMemoryRealtimeConnections connections = started(properties(10, 5), held::add);
        RecordingRealtimeChannel slow = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, slow);

        for (int i = 0; i < 100; i++) {
            connections.sendToAccount(1L, new NotificationRead(NOW));
        }

        assertThat(closed("outbox_full")).isEqualTo(1);
        assertThat(open(ClientType.DESKTOP_WEB)).isZero();

        held.forEach(Runnable::run);

        assertThat(slow.isClosed()).isTrue();
        assertThat(slow.sent()).isEmpty();
    }

    @Test
    @DisplayName("화면이 연결을 끊으면 목록에서 빼고 허가를 돌려준다")
    void removesConnectionClosedByClient() {
        InMemoryRealtimeConnections connections = started(properties(1, 5));
        RecordingRealtimeChannel channel = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.APP, channel);

        channel.disconnect();
        connections.heartbeat();

        assertThat(open(ClientType.APP)).isZero();
        assertThat(channel.heartbeats()).isZero();
        assertThat(connections.register(2L, ORGANIZATION_ID, ClientType.APP, new RecordingRealtimeChannel()))
                .isTrue();
    }

    @Test
    @DisplayName("시작 전과 종료 뒤에는 연결을 받지 않고, 종료하면 모든 연결에 reconnect를 보내고 닫는다")
    void stopsAcceptingAndReconnectsAllOnShutdown() {
        InMemoryRealtimeConnections connections = connections(properties(10, 5), Runnable::run);

        assertThat(connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                .isFalse();

        connections.start();
        RecordingRealtimeChannel first = new RecordingRealtimeChannel();
        RecordingRealtimeChannel second = new RecordingRealtimeChannel();
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, first);
        connections.register(2L, ORGANIZATION_ID, ClientType.MOBILE_WEB, second);

        connections.stop();

        assertThat(connections.isRunning()).isFalse();
        assertThat(first.sent()).containsExactly(new Connected(), new Reconnect());
        assertThat(second.sent()).containsExactly(new Connected(), new Reconnect());
        assertThat(first.isClosed()).isTrue();
        assertThat(second.isClosed()).isTrue();
        assertThat(connections.register(3L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel()))
                .isFalse();
        assertThat(rejected("shutting_down")).isEqualTo(2);
        assertThat(closed("shutdown")).isEqualTo(2);
    }

    @Test
    @DisplayName("허가 수와 목록 크기가 맞으면 어긋남을 세지 않는다")
    void countsNoDriftWhenPermitsMatchConnections() {
        InMemoryRealtimeConnections connections = started(properties(10, 5));
        connections.register(1L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, new RecordingRealtimeChannel());
        RecordingRealtimeChannel closing = new RecordingRealtimeChannel();
        connections.register(2L, ORGANIZATION_ID, ClientType.DESKTOP_WEB, closing);
        closing.disconnect();

        connections.heartbeat();
        connections.heartbeat();

        assertThat(meters.get("realtime.connections.drift").counter().count()).isZero();
    }

    private InMemoryRealtimeConnections started(RealtimeConnectionProperties properties) {
        return started(properties, Runnable::run);
    }

    private InMemoryRealtimeConnections started(RealtimeConnectionProperties properties, Executor delivery) {
        InMemoryRealtimeConnections connections = connections(properties, delivery);
        connections.start();
        return connections;
    }

    private InMemoryRealtimeConnections connections(RealtimeConnectionProperties properties, Executor delivery) {
        return new InMemoryRealtimeConnections(properties, meters, clock, () -> delivery, null);
    }

    private static RealtimeConnectionProperties properties(int maxConnections, int maxConnectionsPerAccount) {
        return new RealtimeConnectionProperties(maxConnections, maxConnectionsPerAccount, null, null, null, null);
    }

    private double open(ClientType clientType) {
        return meters.get("realtime.connections")
                .tag("client", clientType.tag())
                .gauge()
                .value();
    }

    private double closed(String reason) {
        var counter =
                meters.find("realtime.connections.closed").tag("reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }

    private double rejected(String reason) {
        var counter = meters.find("realtime.connections.rejected")
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
