package com.orbit.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.DeliveryStatus;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationDelivery;
import com.orbit.notification.domain.NotificationType;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 집기·결과 저장이 각자 커밋하는지 보려고 테스트 트랜잭션 없이 실행하고, 매번 테이블을 비운다. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("알림 발신 대기 저장")
class NotificationDeliveryPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Instant LEASE_UNTIL = NOW.plus(Duration.ofMinutes(5));
    private static final Long RECIPIENT = 20L;

    @Autowired
    private NotificationDeliveryPersistenceAdapter adapter;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private PushNotificationPort pushNotificationPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDeliveries() {
        jdbcTemplate.update("delete from notification_deliveries");
        jdbcTemplate.update("delete from notifications");
    }

    @Test
    @DisplayName("발신 대기는 부른 쪽 트랜잭션에 들어가 함께 커밋되고 롤백되면 남지 않는다")
    void addJoinsCallerTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adapter.add(NotificationDelivery.pendingPush(1L, RECIPIENT, NOW));
            status.setRollbackOnly();
        });
        assertThat(count()).as("알림 저장이 롤백되면 발신 대기도 남지 않는다").isZero();

        NotificationDelivery added = new TransactionTemplate(transactionManager)
                .execute(status -> adapter.add(NotificationDelivery.pendingPush(2L, RECIPIENT, NOW)));

        assertThat(added.id()).isPresent();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("푸시 요청은 알림을 저장하는 트랜잭션에 발신 대기로 들어가 바로 보낼 수 있고, 롤백되면 알림과 함께 남지 않는다")
    void pushRequestEnqueuesDeliveryInNotificationTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            pushNotificationPort.send(notifications.save(notification()));
            status.setRollbackOnly();
        });
        assertThat(count()).as("알림 저장이 롤백되면 발신 대기도 남지 않는다").isZero();

        Long notificationId = new TransactionTemplate(transactionManager).execute(status -> {
            Notification saved = notifications.save(notification());
            pushNotificationPort.send(saved);
            return saved.id().orElseThrow();
        });

        assertThat(adapter.claimDue(Instant.now(), Instant.now().plus(Duration.ofMinutes(5)), 10))
                .singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.notificationId()).isEqualTo(notificationId);
                    assertThat(delivery.recipientAccountId()).isEqualTo(RECIPIENT);
                });
    }

    @Test
    @DisplayName("보낼 때가 된 대기만 오래된 순으로 집어 발신 중으로 커밋한다")
    void claimsOnlyDuePendingInOrder() {
        add(1L, NOW.minusSeconds(10));
        add(2L, NOW.minusSeconds(20));
        add(3L, NOW.plusSeconds(1));

        List<NotificationDelivery> claimed = adapter.claimDue(NOW, LEASE_UNTIL, 10);

        assertThat(claimed)
                .extracting(delivery -> delivery.notificationId())
                .as("다음 시도 시각이 이른 순서로 집고, 아직 때가 아닌 대기는 집지 않는다")
                .containsExactly(2L, 1L);
        assertThat(claimed).allSatisfy(delivery -> {
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.IN_FLIGHT);
            assertThat(delivery.attempts()).isEqualTo(1);
            assertThat(delivery.nextAttemptAt()).isEqualTo(LEASE_UNTIL);
        });
        assertThat(statusOf(1L)).isEqualTo("IN_FLIGHT");
        assertThat(statusOf(3L)).isEqualTo("PENDING");
        assertThat(adapter.claimDue(NOW, LEASE_UNTIL, 10))
                .as("점유 중인 발신은 다시 집지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("한 번에 집는 건수를 제한한다")
    void limitsClaimSize() {
        for (long id = 1; id <= 3; id++) {
            add(id, NOW);
        }

        assertThat(adapter.claimDue(NOW, LEASE_UNTIL, 2)).hasSize(2);
        assertThat(adapter.claimDue(NOW, LEASE_UNTIL, 2)).hasSize(1);
    }

    @Test
    @DisplayName("점유가 만료된 발신 중 행은 다시 집고 시도 횟수를 늘린다")
    void reclaimsExpiredInFlightDelivery() {
        add(1L, NOW);
        adapter.claimDue(NOW, LEASE_UNTIL, 10);

        List<NotificationDelivery> reclaimed =
                adapter.claimDue(LEASE_UNTIL, LEASE_UNTIL.plus(Duration.ofMinutes(5)), 10);

        assertThat(reclaimed).singleElement().satisfies(delivery -> {
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.IN_FLIGHT);
            assertThat(delivery.attempts()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("두 인스턴스가 동시에 집어도 같은 행을 두 번 집지 않는다")
    void concurrentClaimsAreDisjoint() throws Exception {
        for (long id = 1; id <= 20; id++) {
            add(id, NOW);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<NotificationDelivery>>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return adapter.claimDue(NOW, LEASE_UNTIL, 15);
                }));
            }
            start.countDown();

            List<Long> claimedIds = new ArrayList<>();
            for (Future<List<NotificationDelivery>> result : results) {
                result.get(10, TimeUnit.SECONDS).forEach(delivery -> claimedIds.add(delivery.notificationId()));
            }
            assertThat(claimedIds).doesNotHaveDuplicates().hasSize(20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("결과는 집은 시점과 같은 점유일 때만 저장한다")
    void savesResultOnlyForCurrentClaim() {
        add(1L, NOW);
        NotificationDelivery firstClaim = adapter.claimDue(NOW, LEASE_UNTIL, 10).getFirst();
        NotificationDelivery secondClaim = adapter.claimDue(LEASE_UNTIL, LEASE_UNTIL.plus(Duration.ofMinutes(5)), 10)
                .getFirst();

        assertThat(adapter.saveResult(firstClaim.markSent("late", NOW)))
                .as("점유가 만료돼 다른 인스턴스가 다시 집은 뒤 도착한 결과는 버린다")
                .isFalse();
        assertThat(statusOf(1L)).isEqualTo("IN_FLIGHT");

        assertThat(adapter.saveResult(secondClaim.markSent("provider-2", LEASE_UNTIL)))
                .isTrue();
        assertThat(statusOf(1L)).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                        "select provider_message_id from notification_deliveries where notification_id = 1",
                        String.class))
                .isEqualTo("provider-2");
    }

    @Test
    @DisplayName("다시 보낼 실패는 대기로 되돌려 다음 시도 시각에 다시 집는다")
    void retryReturnsDeliveryToQueue() {
        add(1L, NOW);
        NotificationDelivery claimed = adapter.claimDue(NOW, LEASE_UNTIL, 10).getFirst();
        Instant retryAt = NOW.plusSeconds(30);

        adapter.saveResult(claimed.retryAt("일시 오류", retryAt));

        assertThat(adapter.claimDue(retryAt.minusMillis(1), LEASE_UNTIL, 10)).isEmpty();
        assertThat(adapter.claimDue(retryAt, LEASE_UNTIL, 10)).singleElement().satisfies(delivery -> {
            assertThat(delivery.attempts()).isEqualTo(2);
            assertThat(delivery.lastError()).contains("일시 오류");
        });
    }

    private void add(Long notificationId, Instant nextAttemptAt) {
        adapter.add(NotificationDelivery.pendingPush(notificationId, RECIPIENT, nextAttemptAt));
    }

    private static Notification notification() {
        return Notification.create(RECIPIENT, 1L, NotificationType.WORK_ASSIGNED, 2L, "배정 알림", NOW);
    }

    private String statusOf(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "select status from notification_deliveries where notification_id = ?", String.class, notificationId);
    }

    private long count() {
        return jdbcTemplate.queryForObject("select count(*) from notification_deliveries", Long.class);
    }
}
