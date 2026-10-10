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
import com.orbit.organization.application.port.in.command.CreateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.IntegrationTestSupport;

/**
 * 같은 이름의 유형 생성이 발주사 잠금으로 한 줄로 서는지 두 트랜잭션으로 확인한다. 앞 트랜잭션이 커밋 전에 잠금을 쥔 동안 뒤 요청이 기다리고, 커밋 뒤에는 저장된 유형을
 * 보고 중복으로 거부해야 한다. 잠금이 없으면 뒤 요청이 커밋 전 상태(유형 없음)를 읽고 같은 이름을 하나 더 만든다.
 */
@DisplayName("직원·기사 유형 생성 동시성")
class MemberCategoryLockIntegrationTest extends IntegrationTestSupport {

    private static final long OWNER = 91_001L;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private CreateMemberCategoryUseCase createMemberCategoryUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 이름의 유형을 동시에 만들면 뒤 요청은 기다렸다가 중복으로 거부되고 하나만 남는다")
    void serializesConcurrentCreationWithSameName() throws Exception {
        long organizationId = createOrganizationUseCase
                .create(new CreateOrganizationCommand(OWNER, "동시성 발주사", Industry.HVAC))
                .organizationId();
        CreateMemberCategoryCommand command =
                new CreateMemberCategoryCommand(OWNER, organizationId, MemberCategoryKind.STAFF, "정규직", "#3B82F6");

        CountDownLatch firstCreated = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> firstRequest = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> {
                    createMemberCategoryUseCase.create(command);
                    firstCreated.countDown();
                    await(commitFirst);
                }),
                executor);
        assertThat(firstCreated.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> secondRequest =
                CompletableFuture.runAsync(() -> createMemberCategoryUseCase.create(command), executor);
        assertThatThrownBy(() -> secondRequest.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitFirst.countDown();
        firstRequest.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> secondRequest.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.DUPLICATE_CATEGORY_NAME));
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from company_membership_category where company_id = ? and name = ?",
                        Long.class,
                        organizationId,
                        "정규직"))
                .isEqualTo(1L);
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
