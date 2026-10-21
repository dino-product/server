package com.orbit.schedule.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.profile.AccountWithdrawn;
import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
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
import com.orbit.support.SharedContainers;

/**
 * profile 탈퇴 연동을 실제 트랜잭션·커밋 뒤 비동기 소비로 확인한다. profile 서비스 대신 TransactionTemplate이 탈퇴 트랜잭션을 열고 이벤트를 발행한다. 계정의 기사
 * 계약 조회(organization 계약, HM-321)는 대체하고, 그 조회가 열린 반환 트랜잭션에 완료 기록을 걸어 소비가 끝났는지 기다린다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("탈퇴 차단 판정과 탈퇴 반환 연동(PostgreSQL)")
class AccountWithdrawnIntegrationTest implements SharedContainers {

    // 테스트들이 같은 DB와 비동기 소비를 쓰므로 테스트마다 다른 계정·조직을 써서 앞선 테스트의 작업·소비가 섞이지 않게 한다.
    private static final AtomicLong NEXT_ID = new AtomicLong(900L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(41L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId ASSIGNED_BY = new MembershipId(11L);
    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant TEN = Instant.parse("2030-01-02T01:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // 조립에 필요한 organization 계약. 이 테스트는 요청자·배정할 기사를 확인하지 않는다.
    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    @MockitoBean
    private TechnicianContractLookup technicianContractLookup;

    // 탈퇴한 계정의 기사 계약은 이 대체로 정한다.
    @MockitoBean
    private AccountTechnicianContractsPort accountTechnicianContractsPort;

    @Autowired
    private List<WithdrawalBlocker> withdrawalBlockers;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ConcurrentMap<Long, List<Completion>> completions = new ConcurrentHashMap<>();
    private long accountId;
    private OrganizationId organizationId;

    // 테스트 인스턴스를 테스트끼리 공유해도 테스트마다 새 계정·조직을 쓰도록 필드 초기화가 아니라 매 테스트 전에 정한다.
    @BeforeEach
    void useNewAccountAndContract() {
        accountId = NEXT_ID.getAndIncrement();
        organizationId = new OrganizationId(NEXT_ID.getAndIncrement());
        given(accountTechnicianContractsPort.listByAccount(anyLong())).willAnswer(invocation -> {
            Long requested = invocation.getArgument(0);
            recordCompletionOf(requested);
            return requested.equals(accountId)
                    ? List.of(new AccountTechnicianContract(TECHNICIAN_ID, organizationId, true))
                    : List.of();
        });
    }

    @Test
    @DisplayName("profile의 탈퇴 트랜잭션에서 작업중·배정됨 작업을 사유와 건수로 알린다")
    void reportsBlockingWorksInWithdrawalTransaction() {
        store(WorkStatus.IN_PROGRESS, 0);
        store(WorkStatus.PENDING_ACCEPT, 3);
        store(WorkStatus.PENDING_ACCEPT, 6);
        store(WorkStatus.ACCEPTED, 9);

        List<WithdrawalBlock> blocks = new TransactionTemplate(transactionManager)
                .execute(status -> withdrawalBlockers.stream()
                        .flatMap(blocker -> blocker.blocksFor(accountId).stream())
                        .toList());

        assertThat(blocks)
                .containsExactlyInAnyOrder(
                        new WithdrawalBlock(WithdrawalBlockReason.WORK_IN_PROGRESS, 1),
                        new WithdrawalBlock(WithdrawalBlockReason.WORK_PENDING_ACCEPT, 2));
    }

    @Test
    @DisplayName("탈퇴가 커밋되면 다른 스레드의 별도 트랜잭션에서 배정됨·작업전 작업을 처리자 없이 대기함으로 돌리고 작업중 작업은 남긴다")
    void releasesScheduledWorksAfterWithdrawalCommits() {
        WorkId pending = store(WorkStatus.PENDING_ACCEPT, 0);
        WorkId accepted = store(WorkStatus.ACCEPTED, 3);
        WorkId inProgress = store(WorkStatus.IN_PROGRESS, 6);
        WorkId planned = storePlanned();

        withdraw(true);

        Completion completion = awaitCompletions(accountId, 1).getFirst();
        assertThat(completion.committed()).isTrue();
        assertThat(completion.threadId()).isNotEqualTo(Thread.currentThread().threadId());
        assertReleasedByWithdrawal(pending);
        assertReleasedByWithdrawal(accepted);
        assertThat(load(inProgress).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(load(planned).plannedSchedule()).contains(new PlannedSchedule(null, TEN, null));
    }

    @Test
    @DisplayName("탈퇴가 롤백되면 반환하지 않는다")
    void ignoresRolledBackWithdrawal() {
        WorkId accepted = store(WorkStatus.ACCEPTED, 0);

        withdraw(false);
        // 롤백된 이벤트는 소비 대상이 아니므로, 뒤에 커밋한 다른 계정의 탈퇴가 소비될 때까지 기다린 뒤에도 반환되지 않았는지 본다.
        long otherAccountId = NEXT_ID.getAndIncrement();
        publishCommitted(otherAccountId);
        awaitCompletions(otherAccountId, 1);

        assertThat(completions).doesNotContainKey(accountId);
        assertThat(load(accepted).status()).isEqualTo(WorkStatus.ACCEPTED);
    }

    @Test
    @DisplayName("같은 탈퇴를 다시 받아도 이미 돌린 작업을 건너뛰고 성공한다")
    void redeliveredWithdrawalKeepsResult() {
        WorkId accepted = store(WorkStatus.ACCEPTED, 0);
        withdraw(true);
        awaitCompletions(accountId, 1);
        int historySize = load(accepted).assignmentHistory().size();

        withdraw(true);

        assertThat(awaitCompletions(accountId, 2))
                .allSatisfy(completion -> assertThat(completion.committed()).isTrue());
        assertReleasedByWithdrawal(accepted);
        assertThat(load(accepted).assignmentHistory()).hasSize(historySize);
    }

    /** profile의 탈퇴 트랜잭션처럼 트랜잭션 안에서 이벤트를 발행하고 커밋하거나 롤백한다. */
    private void withdraw(boolean commit) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AccountWithdrawn(accountId, Instant.now()));
            if (!commit) {
                status.setRollbackOnly();
            }
        });
    }

    private void publishCommitted(long withdrawnAccountId) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> eventPublisher.publishEvent(new AccountWithdrawn(withdrawnAccountId, Instant.now())));
    }

    /** 계정의 기사 계약 조회는 반환 트랜잭션 안에서 불리므로, 그 트랜잭션이 끝날 때 커밋 여부와 소비 스레드를 남긴다. */
    private void recordCompletionOf(Long withdrawnAccountId) {
        long threadId = Thread.currentThread().threadId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                completions
                        .computeIfAbsent(withdrawnAccountId, id -> new CopyOnWriteArrayList<>())
                        .add(new Completion(status == STATUS_COMMITTED, threadId));
            }
        });
    }

    private List<Completion> awaitCompletions(long withdrawnAccountId, int count) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            List<Completion> done = completions.getOrDefault(withdrawnAccountId, List.of());
            if (done.size() >= count) {
                return List.copyOf(done);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(
                "withdrawal of account " + withdrawnAccountId + " was not consumed " + count + " times");
    }

    private void assertReleasedByWithdrawal(WorkId id) {
        Work work = load(id);
        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.schedule()).isEmpty();
        assertThat(work.assignmentHistory().getLast().ending()).hasValueSatisfying(ending -> {
            assertThat(ending.endedBy()).isNull();
            assertThat(ending.reason()).isEqualTo(AssignmentEndReason.ACCOUNT_WITHDRAWN);
        });
    }

    private WorkId store(WorkStatus status, int hoursAfterTen) {
        return new TransactionTemplate(transactionManager)
                .execute(tx -> workRepository.save(work(status, hoursAfterTen)))
                .id()
                .orElseThrow();
    }

    private WorkId storePlanned() {
        return new TransactionTemplate(transactionManager)
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
    }

    /** 한 시간 전에 배정하고 그 뒤에 수락·시작한 작업. 반환 시각(지금)이 이 기록들보다 늦도록 과거 시각을 쓴다. */
    private Work work(WorkStatus status, int hoursAfterTen) {
        Instant assignedAt = Instant.now().minus(Duration.ofHours(1));
        Work work = Work.register(organizationId, "반환 대상", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(
                new WorkSchedule(TECHNICIAN_ID, TEN.plus(Duration.ofHours(hoursAfterTen)), TWO_HOURS),
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

    private record Completion(boolean committed, long threadId) {}
}
