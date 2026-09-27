package com.orbit.schedule.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("기사 일정 잠금(PostgreSQL)")
class PostgresTechnicianScheduleLockAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final Duration SHORT_WAIT = Duration.ofMillis(300);
    private static final Duration LONG_WAIT = Duration.ofSeconds(5);
    private static final long TEST_TIMEOUT_SECONDS = 10;
    // 테스트가 실패해 잠금이 남아도 다음 테스트와 키가 겹치지 않도록 테스트마다 새 기사를 쓴다.
    private static final AtomicLong NEXT_TECHNICIAN = new AtomicLong(1_000);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseHolderAndStopThreads() throws InterruptedException {
        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    @DisplayName("같은 기사는 앞선 트랜잭션이 커밋될 때까지 기다린다")
    void sameTechnicianWaitsUntilHolderCommits() throws Exception {
        MembershipId technicianId = nextTechnician();
        Future<?> holder = holdLock(technicianId);

        Future<?> waiter = lockInNewTransaction(technicianId, LONG_WAIT);
        awaitWaitingAdvisoryLock(technicianId);
        assertThat(waiter).isNotDone();

        release.countDown();
        holder.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        waiter.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("다른 기사는 기다리지 않는다")
    void otherTechnicianDoesNotWait() throws Exception {
        holdLock(nextTechnician());

        lockInNewTransaction(nextTechnician(), SHORT_WAIT).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("대기 한도를 넘기면 포트의 기사 일정 변경 중 예외로 실패한다")
    void failsWhenWaitLimitExceeded() throws Exception {
        MembershipId technicianId = nextTechnician();
        holdLock(technicianId);

        Future<?> waiter = lockInNewTransaction(technicianId, SHORT_WAIT);

        assertThatThrownBy(() -> waiter.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(TechnicianScheduleBusyException.class);
    }

    @Test
    @DisplayName("롤백해도 잠금이 풀린다")
    void releasesOnRollback() throws Exception {
        MembershipId technicianId = nextTechnician();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adapter(SHORT_WAIT).lock(ORGANIZATION_ID, technicianId);
            status.setRollbackOnly();
        });

        lockInNewTransaction(technicianId, SHORT_WAIT).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("잠근 뒤에는 트랜잭션이 앞서 정한 대기 한도를 그대로 되돌린다")
    void restoresPreviousLockTimeout() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL lock_timeout = '7s'");
            adapter(SHORT_WAIT).lock(ORGANIZATION_ID, nextTechnician());

            assertThat(jdbcTemplate.queryForObject("SELECT current_setting('lock_timeout')", String.class))
                    .isEqualTo("7s");
        });
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 곧바로 풀리는 잠금이 되므로 거부한다")
    void rejectsLockingOutsideTransaction() {
        assertThatThrownBy(() -> adapter(SHORT_WAIT).lock(ORGANIZATION_ID, nextTechnician()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("technician schedule lock requires an active transaction");
    }

    @Test
    @DisplayName("다른 트랜잭션 매니저의 트랜잭션처럼 저장소와 다른 연결이면 잠금이 곧바로 풀리므로 거부한다")
    void rejectsConnectionOutsideTheTransaction() {
        PlatformTransactionManager otherManager =
                new DataSourceTransactionManager(new DelegatingDataSource(dataSource));

        new TransactionTemplate(otherManager).executeWithoutResult(status -> assertThatThrownBy(
                        () -> adapter(SHORT_WAIT).lock(ORGANIZATION_ID, nextTechnician()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("technician schedule lock requires the transaction's connection"));
    }

    @Test
    @DisplayName("같은 조직·기사는 늘 같은 키, 다른 조직이나 기사는 다른 키가 된다")
    void derivesStableKeys() {
        long key = PostgresTechnicianScheduleLockAdapter.keyOf(ORGANIZATION_ID, new MembershipId(3L));

        assertThat(PostgresTechnicianScheduleLockAdapter.keyOf(new OrganizationId(100L), new MembershipId(3L)))
                .isEqualTo(key);
        assertThat(PostgresTechnicianScheduleLockAdapter.keyOf(new OrganizationId(200L), new MembershipId(3L)))
                .isNotEqualTo(key);
        assertThat(PostgresTechnicianScheduleLockAdapter.keyOf(ORGANIZATION_ID, new MembershipId(4L)))
                .isNotEqualTo(key);
    }

    /** 새 트랜잭션에서 잠근 뒤, release가 열릴 때까지 커밋하지 않고 잡고 있는다. 잠근 것을 확인한 뒤 돌아온다. */
    private Future<?> holdLock(MembershipId technicianId) throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        Future<?> holder =
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    adapter(SHORT_WAIT).lock(ORGANIZATION_ID, technicianId);
                    locked.countDown();
                    awaitRelease();
                }));
        assertThat(locked.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return holder;
    }

    private Future<?> lockInNewTransaction(MembershipId technicianId, Duration waitLimit) {
        return executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> adapter(waitLimit).lock(ORGANIZATION_ID, technicianId)));
    }

    /**
     * 다른 연결이 이 기사의 advisory lock을 얻지 못하고 기다리는 상태가 될 때까지 기다린다. bigint 키는 pg_locks에 상위 32비트(classid)·하위 32비트(objid)로
     * 나뉘어 보인다.
     */
    private void awaitWaitingAdvisoryLock(MembershipId technicianId) throws InterruptedException {
        long key = PostgresTechnicianScheduleLockAdapter.keyOf(ORGANIZATION_ID, technicianId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant deadline = Instant.now().plusSeconds(TEST_TIMEOUT_SECONDS);
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted"
                            + " AND classid::bigint = ? AND objid::bigint = ? AND objsubid = 1",
                    Integer.class,
                    key >>> 32,
                    key & 0xFFFF_FFFFL);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no transaction started waiting for the advisory lock");
    }

    private PostgresTechnicianScheduleLockAdapter adapter(Duration waitLimit) {
        return new PostgresTechnicianScheduleLockAdapter(
                new JdbcTemplate(dataSource), new TechnicianScheduleLockProperties(waitLimit));
    }

    private void awaitRelease() {
        try {
            if (!release.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release was not signalled");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static MembershipId nextTechnician() {
        return new MembershipId(NEXT_TECHNICIAN.incrementAndGet());
    }
}
