package com.orbit.shared.internal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.support.IntegrationTestSupport;

@Import(EventPublicationRegistryIntegrationTest.ProbeListeners.class)
class EventPublicationRegistryIntegrationTest extends IntegrationTestSupport {

    private static final String PUBLICATIONS = "event_publication";
    private static final String ARCHIVE = "event_publication_archive";
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

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** 처리 시도 {@code failuresBeforeSuccess}번까지 실패하고 그 뒤 성공하는 시험 이벤트. */
    record RegistryProbe(String key, int failuresBeforeSuccess) {}

    /** 모듈 리스너가 아닌 커밋 후 리스너만 받는 시험 이벤트. */
    record PlainProbe(String key) {}

    static class RegistryProbeListener {

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        void on(RegistryProbe event) {
            int attempt = attempts.computeIfAbsent(event.key(), key -> new AtomicInteger())
                    .incrementAndGet();
            if (attempt <= event.failuresBeforeSuccess()) {
                throw new IllegalStateException("시험용 처리 실패 " + attempt);
            }
        }

        int attempts(String key) {
            return attempts.getOrDefault(key, new AtomicInteger()).get();
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
