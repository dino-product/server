package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

import com.orbit.organization.application.port.in.command.ApproveParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.DeactivateWithdrawnAccountUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.RevokeOwnerUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionInfo;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestDecisionOutcome;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;
import com.orbit.organization.application.port.in.query.GetWithdrawalBlockingOwnershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.organization.domain.RequestChannel;
import com.orbit.support.IntegrationTestSupport;

/**
 * 탈퇴 판정과 탈퇴 비활성화가 같은 발주사의 다른 변경과 발주사 행 잠금으로 한 줄로 서는지 두 트랜잭션으로 확인한다. 판정이 잠금을 놓으면 탈퇴가 커밋되기 전에
 * 다른 총관리자가 해제될 수 있고, 비활성화가 잠금 전에 시각을 읽으면 기다리는 사이 승인으로 생긴 소속의 참여일보다 앞선 탈퇴 시각이 남는다.
 */
@DisplayName("탈퇴 판정·비활성화 동시성")
class WithdrawalLockIntegrationTest extends IntegrationTestSupport {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private RequestParticipationUseCase requestParticipationUseCase;

    @Autowired
    private ApproveParticipationRequestsUseCase approveUseCase;

    @Autowired
    private RevokeOwnerUseCase revokeOwnerUseCase;

    @Autowired
    private GetWithdrawalBlockingOwnershipsUseCase blockingOwnershipsUseCase;

    @Autowired
    private DeactivateWithdrawnAccountUseCase deactivateUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("탈퇴 트랜잭션의 총관리자 판정이 끝나기 전에는 다른 총관리자의 해제가 기다린다")
    void ownerRevocationWaitsForWithdrawalCheck() throws Exception {
        long withdrawing = 93_001L;
        long otherOwner = 93_002L;
        var created = createOrganizationUseCase.create(
                new CreateOrganizationCommand(withdrawing, "탈퇴 판정 발주사", Industry.HVAC));
        long otherOwnerMembership = insertOwner(created.organizationId(), otherOwner);

        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch commitWithdrawal = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Integer> withdrawal = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    int soleOwnerOrganizations = blockingOwnershipsUseCase
                            .get(new GetWithdrawalBlockingOwnershipsQuery(withdrawing))
                            .soleOwnerOrganizationCount();
                    checked.countDown();
                    await(commitWithdrawal);
                    return soleOwnerOrganizations;
                }),
                executor);
        assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> revocation = CompletableFuture.runAsync(
                () -> revokeOwnerUseCase.revoke(
                        new RevokeOwnerCommand(otherOwner, created.organizationId(), otherOwnerMembership)),
                executor);
        assertThatThrownBy(() -> revocation.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitWithdrawal.countDown();
        assertThat(withdrawal.get(10, TimeUnit.SECONDS)).isZero();
        revocation.get(10, TimeUnit.SECONDS);
        assertThat(jdbcTemplate.queryForObject(
                        "select is_owner from company_memberships where id = ?", Boolean.class, otherOwnerMembership))
                .isFalse();
    }

    @Test
    @DisplayName("승인이 커밋 전인 동안 탈퇴 비활성화는 기다렸다가 그 승인으로 생긴 소속도 참여일보다 늦은 탈퇴 시각으로 비활성화한다")
    void deactivationWaitsForApprovalAndDeactivatesApprovedMembership() throws Exception {
        long owner = 93_101L;
        long applicant = 93_102L;
        var created =
                createOrganizationUseCase.create(new CreateOrganizationCommand(owner, "탈퇴 승인 발주사", Industry.HVAC));
        ParticipationRequestInfo request = requestParticipationUseCase.request(new RequestParticipationCommand(
                applicant, created.companyCode(), ParticipationType.STAFF, RequestChannel.COMPANY_CODE));

        CountDownLatch decided = new CountDownLatch(1);
        CountDownLatch commitApproval = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<List<ParticipationRequestDecisionInfo>> approval = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> {
                    List<ParticipationRequestDecisionInfo> results =
                            approveUseCase.approve(new ApproveParticipationRequestsCommand(
                                    owner,
                                    created.organizationId(),
                                    List.of(new Approval(request.requestId(), ParticipationType.STAFF))));
                    decided.countDown();
                    await(commitApproval);
                    return results;
                }),
                executor);
        assertThat(decided.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deactivation = CompletableFuture.runAsync(
                () -> deactivateUseCase.deactivate(new DeactivateWithdrawnAccountCommand(applicant)), executor);
        assertThatThrownBy(() -> deactivation.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitApproval.countDown();
        assertThat(approval.get(10, TimeUnit.SECONDS))
                .extracting(ParticipationRequestDecisionInfo::outcome)
                .containsExactly(ParticipationRequestDecisionOutcome.APPROVED);
        deactivation.get(10, TimeUnit.SECONDS);
        Map<String, Object> membership = jdbcTemplate.queryForMap(
                "select status, joined_at, withdrawn_at from company_memberships"
                        + " where company_id = ? and member_id = ?",
                created.organizationId(),
                applicant);
        assertThat(membership).containsEntry("status", "DEACTIVATED");
        assertThat((Timestamp) membership.get("withdrawn_at"))
                .isAfterOrEqualTo((Timestamp) membership.get("joined_at"));
    }

    private long insertOwner(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject("""
                insert into company_memberships
                    (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)
                values (?, ?, true, 'ACTIVE', now(), now(), now(), now())
                returning id
                """, Long.class, organizationId, accountId);
    }

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
