package com.orbit.realtime.adapter.in.redis;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 인스턴스가 실시간 신호 채널을 구독한다. 구독 컨테이너는 Bean으로 등록하지 않고 여기서 시작·정지한다.
 *
 * <ul>
 *   <li>시작할 때 Redis에 연결할 수 없어도 기동을 막지 않는다. 컨테이너는 처음 구독에 실패하면 다시 시도하지 않으므로, 별도 스레드에서 연결될 때까지 5초마다
 *       다시 시작한다. 시작은 첫 시도가 끝날 때까지만(최대 3초) 기다려, Redis가 있으면 기동을 마치기 전에 구독이 열린다. 구독하기 전의 신호는 받지 못한다.
 *   <li>구독한 뒤 연결이 끊기면 Redis 클라이언트가 다시 연결하며 다시 구독한다. 듣기만 하는 연결이라 통로만 사라지면 스스로 알 수 없지만, Redis 클라이언트의
 *       기본 TCP keepalive(13초 쉬면 13초 간격으로 3번 확인)가 1분 안에 끊는다. 끊긴 동안의 신호는 받지 못하고 화면의 재조회로 맞춘다.
 *   <li>받은 신호는 Redis 클라이언트의 수신 스레드에서 바로 넘긴다. 연결 목록에 줄 세우기만 하고 기다리지 않으므로 수신을 막지 않으며, 받은 순서대로 넘긴다.
 *   <li>기본 phase라 웹 서버의 정상 종료보다 먼저 구독을 멈춘다. 그 뒤의 신호는 연결 목록도 곧 닫히므로 받을 곳이 없다.
 *   <li>구독 여부를 {@code realtime.subscription.listening}(구독 중 1)으로 내보낸다. 구독 컨테이너의 상태라 0은 처음 구독이 열리기 전(Redis가 없어 다시 시도하는
 *       중 포함)과 멈춘 뒤뿐이다. 구독한 뒤 연결이 끊기면 Redis 클라이언트가 다시 연결하는 동안에도 1이므로, 끊긴 구독은 발행은 되는데 받은 신호가 없는지로 본다.
 * </ul>
 */
@Slf4j
@Component
class RealtimeSignalSubscription implements SmartLifecycle, DisposableBean {

    private final RedisMessageListenerContainer container;
    private final Duration retryInterval;
    private final Duration firstAttemptWait;
    private final Object lifecycleLock = new Object();
    private volatile boolean running;
    private volatile Thread subscriber;

    @Autowired
    RealtimeSignalSubscription(
            RedisConnectionFactory connectionFactory, RedisRealtimeSignalSubscriber listener, MeterRegistry meters) {
        this(connectionFactory, listener, meters, Duration.ofSeconds(5), Duration.ofSeconds(3));
    }

    RealtimeSignalSubscription(
            RedisConnectionFactory connectionFactory,
            RedisRealtimeSignalSubscriber listener,
            MeterRegistry meters,
            Duration retryInterval,
            Duration firstAttemptWait) {
        this.retryInterval = retryInterval;
        this.firstAttemptWait = firstAttemptWait;
        this.container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(new SyncTaskExecutor());
        container.addMessageListener(listener, new ChannelTopic(RedisRealtimeSignalSubscriber.CHANNEL));
        container.afterPropertiesSet();
        Gauge.builder(
                        "realtime.subscription.listening",
                        container,
                        listenerContainer -> listenerContainer.isListening() ? 1 : 0)
                .description("이 인스턴스가 실시간 신호 채널을 구독 중이면 1")
                .register(meters);
    }

    @Override
    public void start() {
        CountDownLatch firstAttempt = new CountDownLatch(1);
        Thread thread = Thread.ofPlatform()
                .name("realtime-signal-subscription")
                .daemon(true)
                .unstarted(() -> subscribeUntilListening(firstAttempt));
        synchronized (lifecycleLock) {
            running = true;
            subscriber = thread;
        }
        thread.start();
        try {
            firstAttempt.await(firstAttemptWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        Thread current;
        synchronized (lifecycleLock) {
            running = false;
            current = subscriber;
            subscriber = null;
        }
        if (current != null) {
            current.interrupt();
        }
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void destroy() throws Exception {
        container.destroy();
    }

    /** 구독이 열려 신호를 받고 있는지. */
    boolean isListening() {
        return container.isListening();
    }

    /** 정지 뒤 곧바로 다시 시작하면 옛 스레드가 남을 수 있어, 지금 맡은 스레드일 때만 컨테이너를 시작·정지한다. */
    private void subscribeUntilListening(CountDownLatch firstAttempt) {
        Thread self = Thread.currentThread();
        try {
            while (running && subscriber == self) {
                try {
                    container.start();
                    synchronized (lifecycleLock) {
                        // 시작하는 사이에 정지했으면 열린 구독을 닫는다. 확인과 닫기 사이에 다시 시작되지 않게 잠근다.
                        if (!running) {
                            container.stop();
                        }
                    }
                    return;
                } catch (RuntimeException exception) {
                    if (subscriber != self) {
                        return;
                    }
                    // 실패한 시작도 시작된 것으로 남아 다시 시작하려면 먼저 멈춰야 한다.
                    container.stop();
                    log.warn(
                            "Realtime signal subscription failed; retrying in {}ms: {}",
                            retryInterval.toMillis(),
                            exception.toString());
                }
                firstAttempt.countDown();
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            firstAttempt.countDown();
        }
    }
}
