package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.CreateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.DeleteWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteWorkTypeCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.support.IntegrationTestSupport;

/**
 * 작업 유형 삭제가 schedule 구현과 함께 한 트랜잭션에서 시작 전 작업의 유형을 비우고, 작업에 유형을 지정하는 쪽의 선택 가능 확인과 한 줄로 서는지 실제
 * 트랜잭션으로 확인한다. schedule의 작업 등록은 아직 임시 어댑터가 모든 유형을 거부하므로, 지정하는 쪽은 TransactionTemplate 안에서 선택 가능 확인 뒤
 * 작업 행을 직접 넣어 흉내 낸다.
 */
@RecordApplicationEvents
@DisplayName("작업 유형 삭제와 schedule 연동(PostgreSQL)")
class WorkTypeDeletionIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong NEXT_OWNER = new AtomicLong(92_000L);
    private static final long TIMEOUT_SECONDS = 10;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private CreateWorkTypeUseCase createWorkTypeUseCase;

    @Autowired
    private DeleteWorkTypeUseCase deleteWorkTypeUseCase;

    @Autowired
    private WorkTypeLookup workTypeLookup;

    @Autowired
    private WorkTypeRelease workTypeRelease;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("삭제하면 시작 전 작업만 유형을 비우고, 비운 작업과 담당 기사를 담은 삭제 이벤트를 한 번 발행한다")
    void publishesDeletionWithReleasedWorks() {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        long registered = insertWork(organizationId, workType, "REGISTERED", 31L);
        long accepted = insertWork(organizationId, workType, "ACCEPTED", 21L);
        insertWork(organizationId, workType, "IN_PROGRESS", 22L);

        deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organizationId, workType));
        deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organizationId, workType));

        List<WorkTypeDeleted> published = events.stream(WorkTypeDeleted.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().organizationId()).isEqualTo(organizationId);
        assertThat(published.getFirst().workTypeId()).isEqualTo(workType);
        assertThat(published.getFirst().releasedWorks())
                .containsExactly(new WorkTypeReleasedWork(registered, null), new WorkTypeReleasedWork(accepted, 21L));
    }

    @Test
    @DisplayName("선택 가능 확인을 마친 작업이 커밋되기 전에 들어온 삭제는 기다렸다가 그 작업의 유형까지 비운다")
    void deletionWaitsForWorkThatCheckedSelectable() throws Exception {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch commitWork = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Long> workRequest = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    assertThat(workTypeLookup.isSelectable(organizationId, workType))
                            .isTrue();
                    long work = insertWork(organizationId, workType, "REGISTERED", null);
                    checked.countDown();
                    await(commitWork);
                    return work;
                }),
                executor);
        assertThat(checked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deleteRequest = CompletableFuture.runAsync(
                () -> deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organizationId, workType)),
                executor);
        assertThatThrownBy(() -> deleteRequest.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitWork.countDown();
        long work = workRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        deleteRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(jdbcTemplate.queryForObject("select job_type_id from work where id = ?", Long.class, work))
                .isNull();
    }

    @Test
    @DisplayName("삭제가 커밋되기 전에 들어온 선택 가능 확인은 기다렸다가 삭제된 유형이라 고를 수 없다고 답한다")
    void selectableCheckWaitsForDeletion() throws Exception {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commitDeletion = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> deleteRequest = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> {
                    deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organizationId, workType));
                    deleted.countDown();
                    await(commitDeletion);
                }),
                executor);
        assertThat(deleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> selectable = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> workTypeLookup.isSelectable(organizationId, workType)), executor);
        assertThatThrownBy(() -> selectable.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitDeletion.countDown();
        deleteRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(selectable.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("삭제가 롤백되면 schedule이 비운 작업 유형도 함께 롤백된다")
    void rollsBackReleaseWithDeletion() {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        long work = insertWork(organizationId, workType, "REGISTERED", null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organizationId, workType));
            status.setRollbackOnly();
        });

        assertThat(jobTypeOf(work)).isEqualTo(workType);
        assertThat(jdbcTemplate.queryForObject(
                        "select is_deleted from work_type where id = ?", Boolean.class, workType))
                .isFalse();
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 해제를 부르면 유형 삭제와 따로 커밋되므로 거부한다")
    void rejectsReleaseWithoutCallerTransaction() {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        long work = insertWork(organizationId, workType, "REGISTERED", null);

        assertThatThrownBy(() -> workTypeRelease.releaseForDeletion(organizationId, workType))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jobTypeOf(work)).isEqualTo(workType);
    }

    @Test
    @DisplayName("바깥 트랜잭션이 REPEATABLE READ면 기다린 뒤 커밋된 작업을 못 보므로 해제를 거부한다")
    void rejectsReleaseInCallerTransactionAboveReadCommitted() {
        long owner = NEXT_OWNER.incrementAndGet();
        long organizationId = organization(owner);
        long workType = workType(owner, organizationId, "설치");
        long work = insertWork(organizationId, workType, "REGISTERED", null);
        TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        assertThatThrownBy(() -> repeatableRead.executeWithoutResult(
                        status -> workTypeRelease.releaseForDeletion(organizationId, workType)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jobTypeOf(work)).isEqualTo(workType);
    }

    private Long jobTypeOf(long work) {
        return jdbcTemplate.queryForObject("select job_type_id from work where id = ?", Long.class, work);
    }

    private long organization(long owner) {
        return createOrganizationUseCase
                .create(new CreateOrganizationCommand(owner, "작업 유형 삭제 발주사", Industry.HVAC))
                .organizationId();
    }

    private long workType(long owner, long organizationId, String name) {
        return createWorkTypeUseCase
                .create(new CreateWorkTypeCommand(owner, organizationId, name, "#3B82F6"))
                .workTypeId();
    }

    private long insertWork(long organizationId, long workTypeId, String status, Long technicianId) {
        Timestamp at = Timestamp.from(Instant.parse("2026-10-06T00:00:00Z"));
        jdbcTemplate.update(
                "insert into work (version, company_id, created_by, registered_at, job_type_id, name, status,"
                        + " technician_id, created_at, updated_at) values (0, ?, 1, ?, ?, '작업', ?, ?, ?, ?)",
                organizationId,
                at,
                workTypeId,
                status,
                technicianId,
                at,
                at);
        return jdbcTemplate.queryForObject("select max(id) from work where company_id = ?", Long.class, organizationId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the test to continue");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
