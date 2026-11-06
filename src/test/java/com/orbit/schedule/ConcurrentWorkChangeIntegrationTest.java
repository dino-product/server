package com.orbit.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.media.MediaFiles;
import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianContractLookup;
import com.orbit.organization.TechnicianMember;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.AcceptWorkUseCase;
import com.orbit.schedule.application.port.in.command.CancelWorkUseCase;
import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.SharedContainers;

/**
 * 같은 작업을 두 트랜잭션이 동시에 바꿀 때 저장 자리의 버전 충돌(HM-371)을 실제 PostgreSQL로 확인한다. 먼저 바꾼 쪽은 바깥 트랜잭션에 참여시켜 작업 행을 갱신한
 * 채 커밋을 늦추고, 그동안 들어온 요청은 그 행의 갱신에서 기다리다가 앞선 커밋 뒤 버전이 달라 충돌한다. 충돌한 요청은 새 트랜잭션에서 한 번 다시 실행해 다시 읽은
 * 상태로 결과를 정하고, 롤백된 첫 시도의 이벤트는 커밋 뒤 동작에 닿지 않아 커밋된 변경의 이벤트만 한 번씩 남는지 함께 본다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Import(ConcurrentWorkChangeIntegrationTest.CommittedEvents.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("같은 작업의 동시 변경(PostgreSQL)")
class ConcurrentWorkChangeIntegrationTest implements SharedContainers {

    // 테스트들이 같은 DB를 쓰므로 테스트마다 다른 조직을 써서 앞선 테스트의 작업·이벤트가 섞이지 않게 한다.
    private static final AtomicLong NEXT_ORGANIZATION_ID = new AtomicLong(900L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(31L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId ASSIGNED_BY = new MembershipId(11L);
    private static final long TECHNICIAN_ACCOUNT_ID = 41L;
    private static final long OWNER_ACCOUNT_ID = 42L;
    private static final Instant TEN = Instant.parse("2030-01-02T01:00:00Z");
    private static final long TEST_TIMEOUT_SECONDS = 10;

    @MockitoBean
    private OrganizationMemberLookup memberLookup;

    // media 공개 계약. 사진을 보내지 않으므로 확인은 부르지 않고, 연결은 빈 목록으로 부른 횟수만 본다.
    @MockitoBean
    private MediaFiles mediaFiles;

    @MockitoBean
    private TechnicianContractLookup technicianContractLookup;

    @Autowired
    private SubmitCompletionReportUseCase submitCompletionReportUseCase;

    @Autowired
    private CancelWorkUseCase cancelWorkUseCase;

    @Autowired
    private AcceptWorkUseCase acceptWorkUseCase;

    @Autowired
    private UpdateWorkDetailsUseCase updateWorkDetailsUseCase;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private CommittedEvents committedEvents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private ExecutorService executor;
    private CountDownLatch releaseHolder;
    private OrganizationId organizationId;

    @BeforeEach
    void useNewOrganizationAndThreads() {
        organizationId = new OrganizationId(NEXT_ORGANIZATION_ID.getAndIncrement());
        executor = Executors.newFixedThreadPool(2);
        releaseHolder = new CountDownLatch(1);
        committedEvents.clear();
        when(memberLookup.findActiveMember(TECHNICIAN_ACCOUNT_ID, organizationId.value()))
                .thenReturn(Optional.of(new TechnicianMember(TECHNICIAN_ID.value(), organizationId.value())));
        when(memberLookup.findActiveMember(OWNER_ACCOUNT_ID, organizationId.value()))
                .thenReturn(Optional.of(new StaffMember(REGISTRAR_ID.value(), organizationId.value(), true)));
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseHolder.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    @DisplayName("같은 완료보고를 동시에 두 번 보내면 뒤 요청은 다시 읽어 중복 전송으로 성공하고, 제출 이벤트와 사진 연결은 한 번뿐이다")
    void treatsConcurrentSameReportAsResend() throws Exception {
        WorkId id = store(WorkStatus.IN_PROGRESS);

        Future<?> later = whileHolding(
                () -> submitCompletionReportUseCase.submit(report(id)),
                () -> submitCompletionReportUseCase.submit(report(id)));

        later.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(load(id).status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(committedEvents.committed(CompletionReportSubmitted.class)).hasSize(1);
        assertThat(committedEvents.committed(WorkChanged.class)).hasSize(1);
        assertThat(committedEvents.publishedCount()).isEqualTo(committedEvents.committedCount());
        verify(mediaFiles, times(1)).attach(any(), any());
    }

    @Test
    @DisplayName("취소가 커밋되기 전에 들어온 완료보고는 다시 읽은 취소된 작업을 보고 상태 오류이고, 취소의 이벤트만 남으며 사진은 연결하지 않는다")
    void rejectsReportAfterConcurrentCancellation() throws Exception {
        WorkId id = store(WorkStatus.IN_PROGRESS);

        Future<?> later = whileHolding(
                () -> cancelWorkUseCase.cancel(new CancelWorkCommand(
                        OWNER_ACCOUNT_ID,
                        organizationId.value(),
                        id.value(),
                        CancellationReason.CUSTOMER_REQUEST,
                        null)),
                () -> submitCompletionReportUseCase.submit(report(id)));

        assertThatThrownBy(() -> later.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.INVALID_WORK_STATE));
        assertThat(load(id).status()).isEqualTo(WorkStatus.CANCELED);
        assertThat(committedEvents.committed(WorkCancelled.class)).hasSize(1);
        assertThat(committedEvents.committed(CompletionReportSubmitted.class)).isEmpty();
        assertThat(committedEvents.committed(WorkChanged.class)).hasSize(1);
        assertThat(committedEvents.publishedCount()).isEqualTo(committedEvents.committedCount());
        verify(mediaFiles, never()).attach(any(), any());
    }

    @Test
    @DisplayName("기본정보 수정이 커밋되기 전에 들어온 수락은 다시 실행해 성공하고, 두 변경이 모두 남으며 각자의 이벤트가 한 번씩 남는다")
    void acceptsAfterConcurrentDetailsChange() throws Exception {
        WorkId id = store(WorkStatus.PENDING_ACCEPT);

        Future<?> later = whileHolding(
                () -> updateWorkDetailsUseCase.update(new UpdateWorkDetailsCommand(
                        OWNER_ACCOUNT_ID,
                        organizationId.value(),
                        id.value(),
                        "보일러 점검",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)),
                () -> acceptWorkUseCase.accept(
                        new AcceptWorkCommand(TECHNICIAN_ACCOUNT_ID, organizationId.value(), id.value(), 1)));

        later.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Work work = load(id);
        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.name()).isEqualTo("보일러 점검");
        assertThat(committedEvents.committed(WorkAccepted.class)).hasSize(1);
        assertThat(committedEvents.committed(WorkChanged.class)).hasSize(2);
        assertThat(committedEvents.publishedCount()).isEqualTo(committedEvents.committedCount());
    }

    /**
     * {@code first}를 바깥 트랜잭션에서 실행해 작업 행을 갱신한 채 커밋을 늦추고, 그동안 {@code later}를 보내 행 잠금에서 기다리게 한 뒤 {@code first}를
     * 커밋한다. {@code later}의 결과를 돌려준다.
     */
    private Future<?> whileHolding(Runnable first, Runnable later) throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        Future<?> holder =
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    first.run();
                    changed.countDown();
                    awaitHolderRelease();
                }));
        assertThat(changed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        Future<?> request = executor.submit(later);
        awaitWaitingRowLock();
        assertThat(request).isNotDone();
        releaseHolder.countDown();
        holder.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return request;
    }

    private SubmitCompletionReportCommand report(WorkId id) {
        return new SubmitCompletionReportCommand(
                TECHNICIAN_ACCOUNT_ID, organizationId.value(), id.value(), 1, null, null, "필터 1개", "교체 완료", null, null);
    }

    /** 한 시간 전에 기사 {@link #TECHNICIAN_ID}에게 배정하고 그 뒤에 수락·시작한 작업. 요청 시각(지금)이 이 기록들보다 늦다. */
    private WorkId store(WorkStatus status) {
        Instant assignedAt = Instant.now().minus(Duration.ofHours(1));
        Work work =
                Work.register(organizationId, "동시 변경 대상", REGISTRAR_ID, assignedAt.minusSeconds(60), null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)), assignedAt, ASSIGNED_BY);
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS) {
            work.accept(assignedAt.plusSeconds(60));
        }
        if (status == WorkStatus.IN_PROGRESS) {
            work.start(assignedAt.plusSeconds(120));
        }
        return new TransactionTemplate(transactionManager)
                .execute(tx -> workRepository.save(work))
                .id()
                .orElseThrow();
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

    /** 이 DB에서 행 갱신이 다른 트랜잭션의 커밋을 기다리는 연결이 생길 때까지 기다린다. 기사 잠금(advisory) 등 다른 대기와 섞이지 않도록 대기 종류로 거른다. */
    private void awaitWaitingRowLock() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant deadline = Instant.now().plusSeconds(TEST_TIMEOUT_SECONDS);
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                            + " AND wait_event_type = 'Lock' AND wait_event = 'transactionid'",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("row lock waiter did not appear");
    }

    /**
     * schedule 공개 이벤트를 발행 때와 커밋 뒤로 나눠 기록한다(테스트마다 비움). 실제 커밋 뒤 리스너(보드 캐시 무효화·실시간 신호)처럼 발행 트랜잭션에 커밋 뒤
     * 동작을 걸므로, 롤백된 시도의 이벤트는 커밋 기록에 남지 않는다.
     */
    static class CommittedEvents {

        private final List<Object> published = new CopyOnWriteArrayList<>();
        private final List<Object> committed = new CopyOnWriteArrayList<>();

        @EventListener
        void on(WorkChanged event) {
            record(event);
        }

        @EventListener
        void on(WorkAccepted event) {
            record(event);
        }

        @EventListener
        void on(WorkCancelled event) {
            record(event);
        }

        @EventListener
        void on(CompletionReportSubmitted event) {
            record(event);
        }

        private void record(Object event) {
            published.add(event);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed.add(event);
                }
            });
        }

        void clear() {
            published.clear();
            committed.clear();
        }

        <T> List<T> committed(Class<T> type) {
            return committed.stream().filter(type::isInstance).map(type::cast).toList();
        }

        long publishedCount() {
            return published.size();
        }

        long committedCount() {
            return committed.size();
        }
    }
}
