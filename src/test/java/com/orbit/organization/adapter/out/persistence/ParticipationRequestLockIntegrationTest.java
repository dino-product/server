package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.IntegrationTestSupport;

/**
 * 같은 계정이 같은 발주사에 동시에 보낸 참여 요청이 발주사 행 잠금으로 한 줄로 서서 승인 대기 요청이 하나만 남는지 두 트랜잭션으로 확인한다. 잠금 없이 확인하면 두
 * 요청 모두 승인 대기 요청이 없다고 보고 둘 다 만든다.
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
