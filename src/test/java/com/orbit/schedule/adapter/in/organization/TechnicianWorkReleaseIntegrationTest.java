package com.orbit.schedule.adapter.in.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.TechnicianWorkReleaseResult;
import com.orbit.organization.WorkReleaseBlocked;
import com.orbit.organization.WorkReleased;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.TestcontainersConfiguration;

/**
 * organization이 기사 계약을 끝내는 트랜잭션 안에서 요구 인터페이스를 부르는 상황을 실제 트랜잭션·기사 잠금으로 확인한다. organization 서비스 대신
 * TransactionTemplate이 바깥 트랜잭션을 연다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("대기함 반환 요구 인터페이스(PostgreSQL)")
class TechnicianWorkReleaseIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    // 테스트들이 같은 DB를 쓰므로 테스트마다 다른 조직을 써서 앞선 테스트가 남긴 작업·잠금이 섞이지 않게 한다.
    private static final AtomicLong NEXT_ORGANIZATION_ID = new AtomicLong(700L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(31L);
    private static final TechnicianId OTHER_TECHNICIAN_ID = new TechnicianId(32L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId ASSIGNED_BY = new MembershipId(11L);
    private static final long PROCESSED_BY = 21L;
    private static final Instant TEN = Instant.parse("2030-01-02T01:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final long TEST_TIMEOUT_SECONDS = 10;

    // 조립에 필요한 organization 계약. 이 테스트는 요청자와 기사 계약을 확인하지 않는다.
    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    @MockitoBean
    private TechnicianContractLookup technicianContractLookup;

    @Autowired
    private TechnicianWorkRelease technicianWorkRelease;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private LockTechnicianSchedulePort lockTechnicianSchedulePort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private ExecutorService executor;
    private CountDownLatch releaseHolder;
    private OrganizationId organizationId;

    // 테스트 인스턴스를 테스트끼리 공유해도 테스트마다 새 조직·스레드를 쓰도록 필드 초기화가 아니라 매 테스트 전에 정한다.
    @BeforeEach
    void useNewOrganizationAndThreads() {
        organizationId = new OrganizationId(NEXT_ORGANIZATION_ID.getAndIncrement());
        executor = Executors.newFixedThreadPool(2);
        releaseHolder = new CountDownLatch(1);
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseHolder.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    @DisplayName("비활성화와 함께 커밋되면 배정됨·작업전 작업은 대기함으로 가고 미리 입력한 담당기사는 비며, 작업중 작업과 다른 기사의 작업은 그대로 남는다")
    void commitsReleaseWithCallerTransaction() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);
        WorkId accepted = store(WorkStatus.ACCEPTED, TECHNICIAN_ID, 3);
        WorkId inProgress = store(WorkStatus.IN_PROGRESS, TECHNICIAN_ID, 6);
        WorkId otherTechnician = store(WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, 0);
        WorkId planned = new TransactionTemplate(transactionManager)
                .execute(tx -> workRepository.save(Work.register(
                        organizationId,
                        "미리 입력한 작업",
                        REGISTRAR_ID,
                        REGISTERED_AT,
                        null,
                        null,
                        null,
                        new PlannedSchedule(TECHNICIAN_ID, TEN, null))))
                .id()
                .orElseThrow();

        inCallerTransaction(() -> technicianWorkRelease.releaseForDeactivation(
                organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY));

        assertReleased(pending);
        assertReleased(accepted);
        assertThat(load(inProgress).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(load(otherTechnician).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(load(planned).plannedSchedule()).contains(new PlannedSchedule(null, TEN, null));
    }

    @Test
    @DisplayName("역할 변경이 롤백되면 반환도 함께 롤백된다")
    void rollsBackReleaseWithCallerTransaction() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(technicianWorkRelease.releaseForRoleChange(
                            organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY))
                    .isEqualTo(new WorkReleased(List.of(pending.value())));
            status.setRollbackOnly();
        });

        assertThat(load(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("작업중 작업이 있는 기사의 역할 변경은 그 작업을 돌려주고 아무것도 바꾸지 않는다")
    void blocksRoleChangeWithInProgressWork() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);
        WorkId inProgress = store(WorkStatus.IN_PROGRESS, TECHNICIAN_ID, 3);

        TechnicianWorkReleaseResult result = new TransactionTemplate(transactionManager)
                .execute(status -> technicianWorkRelease.releaseForRoleChange(
                        organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY));

        assertThat(result).isEqualTo(new WorkReleaseBlocked(List.of(inProgress.value())));
        assertThat(load(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 반환이 기사 계약 종료와 따로 커밋되므로 거부한다")
    void rejectsCallWithoutCallerTransaction() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);

        assertThatThrownBy(() -> technicianWorkRelease.releaseForDeactivation(
                        organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(load(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("바깥 트랜잭션이 REPEATABLE READ면 잠근 뒤에도 앞선 커밋을 못 보므로 거부한다")
    void rejectsCallerTransactionAboveReadCommitted() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);
        TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        assertThatThrownBy(() ->
                        repeatableRead.executeWithoutResult(status -> technicianWorkRelease.releaseForDeactivation(
                                organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(load(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("같은 기사의 배정이 커밋될 때까지 기다린 뒤 그 배정까지 돌린다")
    void releasesAssignmentCommittedWhileWaiting() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        WorkId[] assignedWhileHolding = new WorkId[1];
        Future<?> holder =
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    lockTechnicianSchedulePort.lock(organizationId, TECHNICIAN_ID);
                    assignedWhileHolding[0] = workRepository
                            .save(work(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0))
                            .id()
                            .orElseThrow();
                    locked.countDown();
                    awaitHolderRelease();
                }));
        assertThat(locked.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        Future<TechnicianWorkReleaseResult> release = executor.submit(() -> new TransactionTemplate(transactionManager)
                .execute(status -> technicianWorkRelease.releaseForRoleChange(
                        organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY)));
        awaitWaitingAdvisoryLock();
        assertThat(release).isNotDone();
        releaseHolder.countDown();
        holder.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(release.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(new WorkReleased(List.of(assignedWhileHolding[0].value())));
        assertReleased(assignedWhileHolding[0]);
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경이 대기 한도 안에 끝나지 않으면 409로 실패하고 바깥 트랜잭션도 롤백된다")
    void failsWhenTechnicianScheduleStaysBusy() throws Exception {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, 0);
        CountDownLatch locked = new CountDownLatch(1);
        executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lockTechnicianSchedulePort.lock(organizationId, TECHNICIAN_ID);
            locked.countDown();
            awaitHolderRelease();
        }));
        assertThat(locked.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> inCallerTransaction(() -> technicianWorkRelease.releaseForDeactivation(
                        organizationId.value(), TECHNICIAN_ID.value(), PROCESSED_BY)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY));
        assertThat(load(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    private void inCallerTransaction(Runnable call) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> call.run());
    }

    private void assertReleased(WorkId id) {
        Work work = load(id);
        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.schedule()).isEmpty();
        assertThat(work.assignmentHistory().getLast().ending()).hasValueSatisfying(ending -> {
            assertThat(ending.endedBy()).isEqualTo(new MembershipId(PROCESSED_BY));
            assertThat(ending.reason()).isEqualTo(AssignmentEndReason.UNASSIGNED);
        });
    }

    private WorkId store(WorkStatus status, TechnicianId technicianId, int hoursAfterTen) {
        return new TransactionTemplate(transactionManager)
                .execute(tx -> workRepository.save(work(status, technicianId, hoursAfterTen)))
                .id()
                .orElseThrow();
    }

    /** 한 시간 전에 배정하고 그 뒤에 수락·시작한 작업. 반환 시각(지금)이 이 기록들보다 늦도록 과거 시각을 쓴다. */
    private Work work(WorkStatus status, TechnicianId technicianId, int hoursAfterTen) {
        Instant assignedAt = Instant.now().minus(Duration.ofHours(1));
        Work work = Work.register(organizationId, "반환 대상", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(
                new WorkSchedule(technicianId, TEN.plus(Duration.ofHours(hoursAfterTen)), TWO_HOURS),
                assignedAt,
                ASSIGNED_BY);
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS) {
            work.accept(assignedAt.plusSeconds(60));
        }
        if (status == WorkStatus.IN_PROGRESS) {
            work.start(assignedAt.plusSeconds(120));
        }
        return work;
    }

    private Work load(WorkId id) {
        return new TransactionTemplate(transactionManager)
                .execute(tx -> workRepository.findInOrganization(organizationId, id))
                .orElseThrow();
    }

    private void awaitHolderRelease() {
        try {
            assertThat(releaseHolder.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 다른 연결이 advisory lock을 얻지 못하고 기다리는 상태가 될 때까지 기다린다. 이 테스트 컨텍스트의 DB에서는 이 테스트만 잠근다. */
    private void awaitWaitingAdvisoryLock() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant deadline = Instant.now().plusSeconds(TEST_TIMEOUT_SECONDS);
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("advisory lock waiter did not appear");
    }
}
