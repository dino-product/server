package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.ApproveParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.CancelParticipationRequestUseCase;
import com.orbit.organization.application.port.in.command.CreateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.DeleteMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.RejectParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.IntegrationTestSupport;

/**
 * 참여 요청을 바꾸는 요청이 동시에 와도 먼저 커밋한 결과만 남는지 두 트랜잭션으로 확인한다. 요청 생성은 발주사 행 잠금으로, 승인·거절·취소는 요청 행 잠금으로 한 줄로
 * 선다. 잠금 없이 확인하면 두 요청 모두 앞 요청의 결과를 보지 못하고 같은 요청을 두 번 처리한다. 승인은 요청 행보다 먼저 발주사 행도 잠가 유형 삭제와 한 줄로 선다.
 */
@DisplayName("참여 요청 동시성")
class ParticipationRequestLockIntegrationTest extends IntegrationTestSupport {

    private static final long OWNER = 92_001L;
    private static final long REQUESTER = 92_002L;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private RequestParticipationUseCase requestParticipationUseCase;

    @Autowired
    private ApproveParticipationRequestsUseCase approveUseCase;

    @Autowired
    private RejectParticipationRequestsUseCase rejectUseCase;

    @Autowired
    private CancelParticipationRequestUseCase cancelUseCase;

    @Autowired
    private CreateMemberCategoryUseCase createCategoryUseCase;

    @Autowired
    private DeleteMemberCategoryUseCase deleteCategoryUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("첫 요청이 커밋 전인 동안 같은 계정의 둘째 요청은 기다렸다가 승인 대기 요청이 있어 거부된다")
    void secondRequestWaitsAndLeavesSinglePendingRequest() throws Exception {
        var created =
                createOrganizationUseCase.create(new CreateOrganizationCommand(OWNER, "동시 요청 발주사", Industry.HVAC));
        RequestParticipationCommand command =
                new RequestParticipationCommand(REQUESTER, created.companyCode(), ParticipationType.TECHNICIAN);

        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<ParticipationRequestInfo> firstRequest = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    ParticipationRequestInfo info = requestParticipationUseCase.request(command);
                    requested.countDown();
                    await(commitFirst);
                    return info;
                }),
                executor);
        assertThat(requested.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<ParticipationRequestInfo> secondRequest =
                CompletableFuture.supplyAsync(() -> requestParticipationUseCase.request(command), executor);
        assertThatThrownBy(() -> secondRequest.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitFirst.countDown();
        firstRequest.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> secondRequest.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.PENDING_REQUEST_EXISTS));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from participation_requests where company_id = ? and member_id = ?",
                        Long.class,
                        created.organizationId(),
                        REQUESTER))
                .isOne();
    }

    @Test
    @DisplayName("두 총관리자가 같은 요청을 동시에 처리하면 나중 요청은 앞 승인이 커밋되기를 기다렸다가 이미 승인된 요청으로 알리고 소속은 하나만 생긴다")
    void secondOwnerWaitsAndSeesFirstApproval() throws Exception {
        Pending pending = pendingRequest("동시 승인 발주사", 92_101L, 92_102L, 92_103L);
        ApproveParticipationRequestsCommand approveAsStaff = new ApproveParticipationRequestsCommand(
                pending.owner(),
                pending.organizationId(),
                List.of(new Approval(pending.requestId(), ParticipationType.STAFF)));

        CountDownLatch decided = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<List<ParticipationRequestDecisionInfo>> first = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    List<ParticipationRequestDecisionInfo> results = approveUseCase.approve(approveAsStaff);
                    decided.countDown();
                    await(commitFirst);
                    return results;
                }),
                executor);
        assertThat(decided.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<List<ParticipationRequestDecisionInfo>> second = CompletableFuture.supplyAsync(
                () -> rejectUseCase.reject(new RejectParticipationRequestsCommand(
                        pending.secondOwner(), pending.organizationId(), List.of(pending.requestId()), "늦은 거절")),
                executor);
        assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitFirst.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
        assertThat(second.get(10, TimeUnit.SECONDS))
                .containsExactly(new ParticipationRequestDecisionInfo(
                        pending.requestId(),
                        ParticipationRequestDecisionOutcome.NOT_PENDING,
                        ParticipationRequestStatus.APPROVED));
        assertThat(jdbcTemplate.queryForMap(
                        "select status, reject_reason from participation_requests where id = ?", pending.requestId()))
                .containsEntry("status", "APPROVED")
                .containsEntry("reject_reason", null);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from company_memberships where company_id = ? and member_id = ?",
                        Long.class,
                        pending.organizationId(),
                        pending.applicant()))
                .isOne();
    }

    @Test
    @DisplayName("승인이 커밋 전인 동안 요청자의 취소는 기다렸다가 승인 대기가 아니어서 거부된다")
    void cancelWaitsForApprovalAndIsRefused() throws Exception {
        Pending pending = pendingRequest("승인 중 취소 발주사", 92_201L, 92_202L, 92_203L);

        CountDownLatch decided = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<List<ParticipationRequestDecisionInfo>> approval = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    List<ParticipationRequestDecisionInfo> results =
                            approveUseCase.approve(new ApproveParticipationRequestsCommand(
                                    pending.owner(),
                                    pending.organizationId(),
                                    List.of(new Approval(pending.requestId(), ParticipationType.TECHNICIAN))));
                    decided.countDown();
                    await(commitFirst);
                    return results;
                }),
                executor);
        assertThat(decided.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<ParticipationRequestInfo> cancel = CompletableFuture.supplyAsync(
                () -> cancelUseCase.cancel(
                        new CancelParticipationRequestCommand(pending.applicant(), pending.requestId())),
                executor);
        assertThatThrownBy(() -> cancel.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitFirst.countDown();
        approval.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> cancel.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.REQUEST_NOT_PENDING));
        assertThat(jdbcTemplate.queryForObject(
                        "select status from technician where company_id = ? and member_id = ?",
                        String.class,
                        pending.organizationId(),
                        pending.applicant()))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("기사 유형 삭제가 커밋 전인 동안 그 유형을 가진 비활성 기사의 재참여 승인은 기다렸다가 계약을 되살리되 삭제된 유형은 되살리지 않는다")
    void rejoinApprovalWaitsForCategoryDeletionAndKeepsItUnassigned() throws Exception {
        long owner = 92_301L;
        long applicant = 92_302L;
        var created =
                createOrganizationUseCase.create(new CreateOrganizationCommand(owner, "재참여 승인 발주사", Industry.HVAC));
        long categoryId = createCategoryUseCase
                .create(new CreateMemberCategoryCommand(
                        owner, created.organizationId(), MemberCategoryKind.TECHNICIAN, "설치", "#EF4444"))
                .categoryId();
        jdbcTemplate.update("""
                insert into technician (company_id, member_id, company_technician_category_id, status, joined_at,
                    status_changed_at, created_at, updated_at)
                values (?, ?, ?, 'DEACTIVATED', now(), now(), now(), now())
                """, created.organizationId(), applicant, categoryId);
        Long requestId = jdbcTemplate.queryForObject("""
                insert into participation_requests
                    (company_id, member_id, desired_type, status, requested_at, created_at, updated_at)
                values (?, ?, 'TECHNICIAN', 'PENDING', now(), now(), now())
                returning id
                """, Long.class, created.organizationId(), applicant);

        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commitDeletion = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> {
                    deleteCategoryUseCase.delete(new DeleteMemberCategoryCommand(
                            owner, created.organizationId(), MemberCategoryKind.TECHNICIAN, categoryId));
                    deleted.countDown();
                    await(commitDeletion);
                }),
                executor);
        assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<List<ParticipationRequestDecisionInfo>> approval = CompletableFuture.supplyAsync(
                () -> approveUseCase.approve(new ApproveParticipationRequestsCommand(
                        owner,
                        created.organizationId(),
                        List.of(new Approval(requestId, ParticipationType.TECHNICIAN)))),
                executor);
        assertThatThrownBy(() -> approval.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitDeletion.countDown();
        deletion.get(10, TimeUnit.SECONDS);
        assertThat(approval.get(10, TimeUnit.SECONDS))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
        assertThat(jdbcTemplate.queryForMap(
                        "select status, company_technician_category_id from technician"
                                + " where company_id = ? and member_id = ?",
                        created.organizationId(),
                        applicant))
                .containsEntry("status", "ACTIVE")
                .containsEntry("company_technician_category_id", null);
    }

    /** 총관리자 둘이 있는 발주사에 신청자의 승인 대기 요청을 하나 만든다. 둘째 총관리자는 지정 유즈케이스 대신 SQL로 넣는다. */
    private Pending pendingRequest(String name, long owner, long secondOwner, long applicant) {
        var created = createOrganizationUseCase.create(new CreateOrganizationCommand(owner, name, Industry.HVAC));
        jdbcTemplate.update("""
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, true, 'ACTIVE', now(), now(), now(), now())
                """, created.organizationId(), secondOwner);
        ParticipationRequestInfo request = requestParticipationUseCase.request(
                new RequestParticipationCommand(applicant, created.companyCode(), ParticipationType.TECHNICIAN));
        return new Pending(created.organizationId(), owner, secondOwner, applicant, request.requestId());
    }

    private record Pending(long organizationId, long owner, long secondOwner, long applicant, long requestId) {}

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to commit");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
