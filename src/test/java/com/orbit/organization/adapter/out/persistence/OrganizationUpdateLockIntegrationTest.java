package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.orbit.organization.application.port.in.command.ChangeCompanyCodeUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.UpdateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationCommand;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.domain.Industry;
import com.orbit.support.IntegrationTestSupport;

/**
 * 발주사 정보 수정과 회사 코드 변경이 같은 발주사 행 잠금으로 한 줄로 서는지 두 트랜잭션으로 확인한다. 정보 수정은 발주사 행 전체를 다시 쓰므로, 잠금 없이 읽으면
 * 늦게 커밋하는 수정이 그 사이 바뀐 회사 코드를 이전 코드로 되돌린다.
 */
@DisplayName("발주사 정보 수정·회사 코드 변경 동시성")
class OrganizationUpdateLockIntegrationTest extends IntegrationTestSupport {

    private static final long OWNER = 91_001L;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private UpdateOrganizationUseCase updateOrganizationUseCase;

    @Autowired
    private ChangeCompanyCodeUseCase changeCompanyCodeUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("정보 수정이 커밋 전인 동안 코드 변경은 기다렸다가 수정된 행에 새 코드를 쓴다")
    void codeChangeWaitsForInfoUpdateAndKeepsNewCode() throws Exception {
        var created = createOrganizationUseCase.create(new CreateOrganizationCommand(OWNER, "동시성 발주사", Industry.HVAC));
        long organizationId = created.organizationId();

        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch commitUpdate = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> updateRequest = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> {
                    updateOrganizationUseCase.update(
                            new UpdateOrganizationCommand(OWNER, organizationId, "새 발주사", Industry.PLUMBING));
                    updated.countDown();
                    await(commitUpdate);
                }),
                executor);
        assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<CompanyCodeInfo> changeRequest = CompletableFuture.supplyAsync(
                () -> changeCompanyCodeUseCase.change(new ChangeCompanyCodeCommand(OWNER, organizationId)), executor);
        assertThatThrownBy(() -> changeRequest.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitUpdate.countDown();
        updateRequest.get(10, TimeUnit.SECONDS);
        String newCode = changeRequest.get(10, TimeUnit.SECONDS).companyCode();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select code, name, industry_code from companies where id = ?", organizationId);
        assertThat(row)
                .containsEntry("code", newCode)
                .containsEntry("name", "새 발주사")
                .containsEntry("industry_code", "PLUMBING");
        assertThat(jdbcTemplate.queryForList(
                        "select code from retired_company_codes where company_id = ?", String.class, organizationId))
                .containsExactly(created.companyCode());
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
