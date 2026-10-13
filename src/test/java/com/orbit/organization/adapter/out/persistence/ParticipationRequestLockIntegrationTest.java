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
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.RejectParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.IntegrationTestSupport;

/**
 * 참여 요청을 바꾸는 요청이 동시에 와도 먼저 커밋한 결과만 남는지 두 트랜잭션으로 확인한다. 요청 생성은 발주사 행 잠금으로, 승인·거절·취소는 요청 행 잠금으로 한 줄로
 * 선다. 잠금 없이 확인하면 두 요청 모두 앞 요청의 결과를 보지 못하고 같은 요청을 두 번 처리한다.
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
