package com.orbit.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.notification.application.port.in.command.DispatchPushDeliveriesUseCase;
import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;
import com.orbit.notification.application.port.out.NotificationDeliveryRepository;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.application.port.out.PushSender;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;
import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;
import com.orbit.notification.domain.RetryBackoff;
import com.orbit.support.IntegrationTestSupport;

/** 주기 작업은 test 프로필에서 꺼져 있어 발신 대기열 처리를 직접 부른다. 푸시 요청은 알림 서비스와 같이 알림 저장 트랜잭션에서 넣는다. */
@Import(PushDeliveryDispatchIntegrationTest.ScriptedPushSenderConfiguration.class)
class PushDeliveryDispatchIntegrationTest extends IntegrationTestSupport {

    private static final Long RECIPIENT = 20L;
    private static final Long RECIPIENT_WITH_UNREGISTERED_DEVICE = 21L;
    private static final Long RECIPIENT_WITHOUT_DEVICE = 22L;
    private static final String RECIPIENT_TOKEN = "dispatch-test-token-20";
    private static final String UNREGISTERED_TOKEN = "dispatch-test-token-21";
    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofMinutes(5);
    private static final RetryBackoff IMMEDIATE_RETRY = new RetryBackoff(Duration.ofMillis(1), Duration.ofMillis(1));
    private static final DispatchPushDeliveriesCommand COMMAND =
            new DispatchPushDeliveriesCommand(10, IN_FLIGHT_TIMEOUT, IMMEDIATE_RETRY);

    @Autowired
    private DispatchPushDeliveriesUseCase dispatcher;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private PushNotificationPort pushNotificationPort;

    @Autowired
    private NotificationDeliveryRepository deliveries;

    @Autowired
    private PushDeviceRepository pushDevices;

    @Autowired
    private ScriptedPushSender pushSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("delete from notification_deliveries");
        jdbcTemplate.update(
                "delete from push_devices where member_id in (?, ?, ?)",
                RECIPIENT,
                RECIPIENT_WITH_UNREGISTERED_DEVICE,
                RECIPIENT_WITHOUT_DEVICE);
        registerDevice(RECIPIENT, RECIPIENT_TOKEN);
        pushSender.reset();
    }

    @Test
    void sendsEachDeliveryOnceWhenTwoDispatchersRunConcurrently() throws Exception {
        List<Long> notificationIds = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            notificationIds.add(requestPush());
        }
        pushSender.delayEachSend(Duration.ofMillis(10));
        DispatchPushDeliveriesCommand smallBatches =
                new DispatchPushDeliveriesCommand(5, IN_FLIGHT_TIMEOUT, IMMEDIATE_RETRY);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(executor.submit(() -> {
                    start.await();
                    for (int round = 0; round < 10; round++) {
                        dispatcher.dispatch(smallBatches);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(countWithStatus("SENT")).as("모든 발신을 보낸다").isEqualTo(30);
        assertThat(pushSender.sendCounts())
                .as("두 디스패처가 동시에 돌아도 같은 발신을 두 번 보내지 않는다")
                .containsOnlyKeys(notificationIds)
                .allSatisfy((id, count) -> assertThat(count).isEqualTo(1));
    }

    @Test
    void keepsRetryingWithoutAttemptLimitUntilSent() {
        Long notificationId = requestPush();
        jdbcTemplate.update(
                "update notification_deliveries set attempts = 1000 where notification_id = ?", notificationId);
        pushSender.script(
                notificationId,
                new PushSendResult.RetryableFailure("제공자 일시 오류"),
                new PushSendResult.RetryableFailure("제공자 인증 오류"),
                new PushSendResult.Sent("provider-1"));

        dispatcher.dispatch(COMMAND);
        assertThat(statusOf(notificationId))
                .as("시도가 1000번을 넘어도 포기하지 않고 다시 대기한다")
                .isEqualTo("PENDING");
        awaitDue();
        dispatcher.dispatch(COMMAND);
        assertThat(statusOf(notificationId)).isEqualTo("PENDING");
        awaitDue();
        dispatcher.dispatch(COMMAND);

        assertThat(statusOf(notificationId)).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                        "select attempts from notification_deliveries where notification_id = ?",
                        Integer.class,
                        notificationId))
                .isEqualTo(1003);
        assertThat(jdbcTemplate.queryForObject(
                        "select last_error from notification_deliveries where notification_id = ?",
                        String.class,
                        notificationId))
                .as("마지막 실패 내용은 보낸 뒤에도 남는다")
                .isEqualTo("제공자 인증 오류");
    }

    @Test
    void resendsDeliveryLeftInFlightAfterLeaseExpires() {
        Long notificationId = requestPush();
        Instant now = Instant.now();
        deliveries.claimDue(now, now.plus(IN_FLIGHT_TIMEOUT), 10);

        dispatcher.dispatch(COMMAND);
        assertThat(pushSender.sendCounts()).as("점유 중인 발신은 보내지 않는다").isEmpty();

        jdbcTemplate.update(
                "update notification_deliveries set next_attempt_at = now() - interval '1 second'"
                        + " where notification_id = ?",
                notificationId);
        dispatcher.dispatch(COMMAND);

        assertThat(statusOf(notificationId)).as("발신 중 멈춘 발신은 점유가 만료되면 다시 보낸다").isEqualTo("SENT");
        assertThat(pushSender.sendCounts()).containsEntry(notificationId, 1);
    }

    @Test
    void recordsSkippedAndFailedDeliveriesAndRemovesUnregisteredDevice() {
        registerDevice(RECIPIENT_WITH_UNREGISTERED_DEVICE, UNREGISTERED_TOKEN);
        Long unregistered = requestPush(RECIPIENT_WITH_UNREGISTERED_DEVICE);
        Long noDevice = requestPush(RECIPIENT_WITHOUT_DEVICE);
        Long malformed = requestPush(RECIPIENT);
        pushSender.script(unregistered, new PushSendResult.Unregistered("UNREGISTERED"));
        pushSender.script(malformed, new PushSendResult.PermanentFailure("요청 형식 오류"));

        dispatcher.dispatch(COMMAND);
        awaitDue();
        dispatcher.dispatch(COMMAND);

        assertThat(statusOf(unregistered)).isEqualTo("SKIPPED");
        assertThat(statusOf(noDevice)).isEqualTo("SKIPPED");
        assertThat(statusOf(malformed)).isEqualTo("FAILED");
        assertThat(pushSender.sendCounts())
                .as("끝난 발신은 다시 보내지 않고, 기기가 없으면 보내지 않는다")
                .containsOnlyKeys(unregistered, malformed)
                .containsEntry(unregistered, 1)
                .containsEntry(malformed, 1);
        assertThat(pushDevices.listByAccountId(RECIPIENT_WITH_UNREGISTERED_DEVICE))
                .as("제공자가 받지 않는 토큰의 기기는 지운다")
                .isEmpty();
        assertThat(pushDevices.listByAccountId(RECIPIENT)).hasSize(1);
    }

    @Test
    void sendsToEveryRegisteredDeviceOfRecipient() {
        registerDevice(RECIPIENT, "dispatch-test-token-20-second");
        Long notificationId = requestPush();

        dispatcher.dispatch(COMMAND);

        assertThat(statusOf(notificationId)).isEqualTo("SENT");
        assertThat(pushSender.sendCounts()).containsEntry(notificationId, 2);
        assertThat(pushSender.tokens()).containsExactlyInAnyOrder(RECIPIENT_TOKEN, "dispatch-test-token-20-second");
    }

    @Test
    void skipsNotificationHiddenWhileWaiting() {
        Long notificationId = requestPush();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Notification notification = notifications.findById(notificationId).orElseThrow();
            notification.hideIfNotifiedBy(Instant.now());
            notifications.save(notification);
        });

        dispatcher.dispatch(COMMAND);

        assertThat(statusOf(notificationId)).as("거절로 숨겨진 알림은 푸시하지 않는다").isEqualTo("SKIPPED");
        assertThat(pushSender.sendCounts()).isEmpty();
    }

    /** 알림 서비스처럼 알림을 저장하는 트랜잭션에서 푸시를 요청하고 알림 식별자를 돌려준다. */
    private Long requestPush() {
        return requestPush(RECIPIENT);
    }

    private Long requestPush(Long recipient) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Notification saved = notifications.save(
                    Notification.create(recipient, 1L, NotificationType.WORK_ASSIGNED, 2L, "배정 알림", Instant.now()));
            pushNotificationPort.send(saved);
            return saved.id().orElseThrow();
        });
    }

    private void registerDevice(Long accountId, String token) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> pushDevices.register(
                        PushDevice.register(accountId, PushPlatform.ANDROID, token, Instant.now())));
    }

    /** 즉시 재시도 대기 시간(1ms)이 지나도록 잠깐 기다린다. */
    private static void awaitDue() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String statusOf(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "select status from notification_deliveries where notification_id = ?", String.class, notificationId);
    }

    private long countWithStatus(String status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification_deliveries where status = ?", Long.class, status);
    }

    /** 알림별로 정해 둔 결과를 차례로 돌려주고, 정해 둔 결과가 없으면 보낸 것으로 돌려주는 시험용 발신 어댑터. */
    static class ScriptedPushSender implements PushSender {

        private final Map<Long, Queue<PushSendResult>> scripts = new ConcurrentHashMap<>();
        private final Map<Long, AtomicInteger> sends = new ConcurrentHashMap<>();
        private final Queue<String> tokens = new ConcurrentLinkedQueue<>();
        private volatile Duration delay = Duration.ZERO;

        @Override
        public PushSendResult send(PushMessage message, String deviceToken) {
            Long notificationId = message.notificationId();
            sends.computeIfAbsent(notificationId, id -> new AtomicInteger()).incrementAndGet();
            tokens.add(deviceToken);
            pause();
            Queue<PushSendResult> scripted = scripts.get(notificationId);
            PushSendResult result = scripted == null ? null : scripted.poll();
            return result != null ? result : new PushSendResult.Sent("provider-" + notificationId);
        }

        void script(Long notificationId, PushSendResult... results) {
            scripts.put(notificationId, new ConcurrentLinkedQueue<>(List.of(results)));
        }

        void delayEachSend(Duration delay) {
            this.delay = delay;
        }

        Map<Long, Integer> sendCounts() {
            Map<Long, Integer> counts = new ConcurrentHashMap<>();
            sends.forEach((id, count) -> counts.put(id, count.get()));
            return counts;
        }

        List<String> tokens() {
            return List.copyOf(tokens);
        }

        void reset() {
            scripts.clear();
            sends.clear();
            tokens.clear();
            delay = Duration.ZERO;
        }

        private void pause() {
            if (delay.isZero()) {
                return;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedPushSenderConfiguration {

        @Bean
        @Primary
        ScriptedPushSender scriptedPushSender() {
            return new ScriptedPushSender();
        }
    }
}
