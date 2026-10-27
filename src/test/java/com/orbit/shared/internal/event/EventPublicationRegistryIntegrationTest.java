package com.orbit.shared.internal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.support.IntegrationTestSupport;

@Import(EventPublicationRegistryIntegrationTest.ProbeListeners.class)
@TestPropertySource(
        properties = {
            "app.events.resubmission.initial-backoff=PT1M",
            "app.events.resubmission.max-backoff=PT30M",
            "spring.modulith.events.staleness.processing=PT1S",
            "spring.modulith.events.staleness.check-interval=PT1S"
        })
class EventPublicationRegistryIntegrationTest extends IntegrationTestSupport {

    private static final String PUBLICATIONS = "event_publication";
    private static final String ARCHIVE = "event_publication_archive";
    private static final Duration INITIAL_BACKOFF = Duration.ofMinutes(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final int ALWAYS_FAIL = Integer.MAX_VALUE;
    private static final Duration ASYNC_WAIT = Duration.ofSeconds(10);

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegistryProbeListener moduleListener;

    @Autowired
    private PlainProbeListener plainListener;

    @Autowired
    private FailedEventPublicationResubmitter resubmitter;

    @Test
    void recordsPublicationInPublishingTransactionAndArchivesItAfterListenerCompletes() {
        String key = newKey();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new RegistryProbe(key, 0));

            assertThat(statuses(PUBLICATIONS, key))
                    .as("발행 트랜잭션 안에서 리스너 몫의 발행 기록을 남긴다")
                    .containsExactly("PUBLISHED");
            assertThat(moduleListener.attempts(key)).as("커밋 전에는 처리하지 않는다").isZero();
        });

        await().atMost(ASYNC_WAIT).untilAsserted(() -> {
            assertThat(statuses(PUBLICATIONS, key))
                    .as("처리를 마친 발행은 기록 테이블에서 빠진다")
                    .isEmpty();
            assertThat(completionDates(ARCHIVE, key))
                    .as("처리를 마친 발행은 이벤트 원문과 완료 시각을 보관 테이블에 남긴다")
                    .singleElement()
                    .isNotNull();
        });
        assertThat(moduleListener.attempts(key)).isEqualTo(1);
    }

    @Test
    void doesNotRecordPublicationOfRolledBackTransaction() {
        String key = newKey();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new RegistryProbe(key, 0));
            status.setRollbackOnly();
        });

        assertThat(statuses(PUBLICATIONS, key)).as("롤백한 발행은 기록을 남기지 않는다").isEmpty();
        assertThat(statuses(ARCHIVE, key)).isEmpty();
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(
                        moduleListener.attempts(key))
                .as("롤백한 발행은 처리하지 않는다")
                .isZero());
    }

    @Test
    void keepsFailedPublicationWhenListenerThrows() {
        String key = newKey();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new RegistryProbe(key, ALWAYS_FAIL)));

        await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(statuses(PUBLICATIONS, key))
                .as("리스너가 실패한 발행은 실패 상태로 남아 다시 보낼 수 있다")
                .containsExactly("FAILED"));
        assertThat(statuses(ARCHIVE, key)).isEmpty();
        assertThat(moduleListener.attempts(key)).isEqualTo(1);
    }

    @Test
    void doesNotRecordPublicationForListenerOtherThanModuleListener() {
        String key = newKey();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new PlainProbe(key)));

        await().atMost(ASYNC_WAIT).until(() -> plainListener.attempts(key) == 1);
        assertThat(statuses(PUBLICATIONS, key))
                .as("모듈 리스너가 아닌 커밋 후 리스너는 발행 기록 대상이 아니다")
                .isEmpty();
        assertThat(statuses(ARCHIVE, key)).isEmpty();
    }

    @Test
    void resubmitsFailedPublicationUntilListenerSucceeds() {
        String key = newKey();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new RegistryProbe(key, 1)));
        await().atMost(ASYNC_WAIT).until(() -> statuses(PUBLICATIONS, key).equals(List.of("FAILED")));
        elapse(key, INITIAL_BACKOFF);

        resubmitter.resubmitFailed();

        await().atMost(ASYNC_WAIT).untilAsserted(() -> {
            assertThat(statuses(PUBLICATIONS, key)).isEmpty();
            assertThat(completionAttempts(ARCHIVE, key))
                    .as("다시 보낸 발행이 처리되면 시도 횟수와 함께 보관된다")
                    .containsExactly(2);
        });
        assertThat(moduleListener.attempts(key)).isEqualTo(2);
    }

    @Test
    void doublesWaitAfterEachFailedAttemptBeforeResubmitting() {
        String key = newKey();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new RegistryProbe(key, ALWAYS_FAIL)));
        awaitFailedAfter(key, 1);

        resubmitter.resubmitFailed();
        assertNoMoreAttemptsThan(key, 1, "첫 실패 뒤 대기 시간이 지나기 전에는 다시 보내지 않는다");

        elapse(key, INITIAL_BACKOFF);
        resubmitter.resubmitFailed();
        awaitFailedAfter(key, 2);

        elapse(key, INITIAL_BACKOFF);
        resubmitter.resubmitFailed();
        assertNoMoreAttemptsThan(key, 2, "두 번째 실패 뒤에는 대기 시간이 두 배로 늘어난다");

        elapse(key, INITIAL_BACKOFF);
        resubmitter.resubmitFailed();
        awaitFailedAfter(key, 3);
    }

    @Test
    void keepsResubmittingWithoutAttemptLimitAtMaxBackoff() {
        String key = newKey();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new RegistryProbe(key, ALWAYS_FAIL)));
        awaitFailedAfter(key, 1);
        jdbcTemplate.update(
                "update " + PUBLICATIONS + " set completion_attempts = 1000 where serialized_event like ?",
                "%" + key + "%");

        elapse(key, MAX_BACKOFF.minusMinutes(1));
        resubmitter.resubmitFailed();
        assertNoMoreAttemptsThan(key, 1, "대기 시간 상한이 지나기 전에는 다시 보내지 않는다");

        elapse(key, Duration.ofMinutes(1));
        resubmitter.resubmitFailed();

        await().atMost(ASYNC_WAIT)
                .alias("시도가 아무리 많아도 대기 시간 상한이 지나면 다시 보낸다")
                .until(() -> moduleListener.attempts(key) == 2);
        await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(statuses(PUBLICATIONS, key))
                .as("다시 실패해도 포기하지 않고 실패 상태로 남겨 다음 재전송을 기다린다")
                .containsExactly("FAILED"));
        assertThat(completionAttempts(PUBLICATIONS, key)).containsExactly(1001);
    }

    @Test
    void marksStalledPublicationFailedSoItCanBeResubmitted() {
        String key = newKey();
        moduleListener.holdFirstAttempt(key);
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> events.publishEvent(new RegistryProbe(key, 0)));

            await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(statuses(PUBLICATIONS, key))
                    .as("처리 중인 채로 멈춘 발행은 실패로 바뀐다")
                    .containsExactly("FAILED"));
            elapse(key, INITIAL_BACKOFF);

            resubmitter.resubmitFailed();

            await().atMost(ASYNC_WAIT).untilAsserted(() -> assertThat(completionAttempts(ARCHIVE, key))
                    .as("실패로 바뀐 발행은 다시 보내 처리된다")
                    .containsExactly(2));
        } finally {
            moduleListener.release(key);
        }
    }

    private void awaitFailedAfter(String key, int attempts) {
        await().atMost(ASYNC_WAIT)
                .until(() -> moduleListener.attempts(key) == attempts
                        && statuses(PUBLICATIONS, key).equals(List.of("FAILED")));
    }

    private void assertNoMoreAttemptsThan(String key, int attempts, String description) {
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                        assertThat(moduleListener.attempts(key)).as(description).isEqualTo(attempts));
    }

    /** 발행·마지막 재전송 시각을 {@code elapsed}만큼 앞당겨 그만큼 시간이 지난 것처럼 만든다. */
    private void elapse(String key, Duration elapsed) {
        jdbcTemplate.update(
                "update " + PUBLICATIONS + " set publication_date = publication_date - ? * interval '1 millisecond',"
                        + " last_resubmission_date = last_resubmission_date - ? * interval '1 millisecond'"
                        + " where serialized_event like ?",
                elapsed.toMillis(),
                elapsed.toMillis(),
                "%" + key + "%");
    }

    private List<String> statuses(String table, String key) {
        return jdbcTemplate.queryForList(
                "select status from " + table + " where serialized_event like ?", String.class, "%" + key + "%");
    }

    private List<Object> completionDates(String table, String key) {
        return jdbcTemplate.queryForList(
                "select completion_date from " + table + " where serialized_event like ?",
                Object.class,
                "%" + key + "%");
    }

    private List<Integer> completionAttempts(String table, String key) {
        return jdbcTemplate.queryForList(
                "select completion_attempts from " + table + " where serialized_event like ?",
                Integer.class,
                "%" + key + "%");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** 처리 시도 {@code failuresBeforeSuccess}번까지 실패하고 그 뒤 성공하는 시험 이벤트. */
    record RegistryProbe(String key, int failuresBeforeSuccess) {}

    /** 모듈 리스너가 아닌 커밋 후 리스너만 받는 시험 이벤트. */
    record PlainProbe(String key) {}

    static class RegistryProbeListener {

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> firstAttemptHolds = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        void on(RegistryProbe event) throws InterruptedException {
            int attempt = attempts.computeIfAbsent(event.key(), key -> new AtomicInteger())
                    .incrementAndGet();
            CountDownLatch hold = attempt == 1 ? firstAttemptHolds.get(event.key()) : null;
            if (hold != null) {
                hold.await(30, TimeUnit.SECONDS);
            }
            if (attempt <= event.failuresBeforeSuccess()) {
                throw new IllegalStateException("시험용 처리 실패 " + attempt);
            }
        }

        int attempts(String key) {
            return attempts.getOrDefault(key, new AtomicInteger()).get();
        }

        /** 첫 처리 시도를 {@link #release}까지 붙잡아 처리 중에 멈춘 발행을 만든다. */
        void holdFirstAttempt(String key) {
            firstAttemptHolds.put(key, new CountDownLatch(1));
        }

        void release(String key) {
            firstAttemptHolds.get(key).countDown();
        }
    }

    static class PlainProbeListener {

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @TransactionalEventListener
        void on(PlainProbe event) {
            attempts.computeIfAbsent(event.key(), key -> new AtomicInteger()).incrementAndGet();
        }

        int attempts(String key) {
            return attempts.getOrDefault(key, new AtomicInteger()).get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeListeners {

        @Bean
        RegistryProbeListener registryProbeListener() {
            return new RegistryProbeListener();
        }

        @Bean
        PlainProbeListener plainProbeListener() {
            return new PlainProbeListener();
        }
    }
}
