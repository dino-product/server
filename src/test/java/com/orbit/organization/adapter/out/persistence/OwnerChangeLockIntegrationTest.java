package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
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
import com.orbit.organization.application.port.in.command.DesignateOwnerUseCase;
import com.orbit.organization.application.port.in.command.RevokeOwnerUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.DesignateOwnerCommand;
import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.IntegrationTestSupport;

/**
 * 총관리자 해제가 발주사 잠금으로 한 줄로 서는지 두 트랜잭션으로 확인한다. 앞 트랜잭션이 커밋 전에 잠금을 쥔 동안 뒤 요청이 기다리고, 커밋 뒤에는 바뀐 총관리자 표시를
 * 보고 판단해야 한다. 잠금이 없으면 뒤 요청이 커밋 전 상태(총관리자 2명)를 읽고 바로 끝나 총관리자가 0명이 된다.
 */
@DisplayName("총관리자 해제 동시성")
class OwnerChangeLockIntegrationTest extends IntegrationTestSupport {

    private static final long FIRST_OWNER = 90_001L;
    private static final long SECOND_OWNER = 90_002L;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private DesignateOwnerUseCase designateOwnerUseCase;

    @Autowired
    private RevokeOwnerUseCase revokeOwnerUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("남은 두 총관리자가 서로를 동시에 해제하면 뒤 요청은 기다렸다가 거부되고 총관리자 1명이 남는다")
    void serializesMutualRevocationOfLastTwoOwners() throws Exception {
        long organizationId = createOrganizationUseCase
                .create(new CreateOrganizationCommand(FIRST_OWNER, "동시성 발주사", Industry.HVAC))
                .organizationId();
        long first = membershipOf(organizationId, FIRST_OWNER);
        long second = staffMembership(organizationId, SECOND_OWNER);
        designateOwnerUseCase.designate(new DesignateOwnerCommand(FIRST_OWNER, organizationId, second));

        CountDownLatch firstRevoked = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Void> firstRequest = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> {
                    revokeOwnerUseCase.revoke(new RevokeOwnerCommand(FIRST_OWNER, organizationId, second));
                    firstRevoked.countDown();
                    await(commitFirst);
                }),
                executor);
        assertThat(firstRevoked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> secondRequest = CompletableFuture.runAsync(
                () -> revokeOwnerUseCase.revoke(new RevokeOwnerCommand(SECOND_OWNER, organizationId, first)), executor);
        assertThatThrownBy(() -> secondRequest.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commitFirst.countDown();
        firstRequest.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> secondRequest.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.OWNER_ONLY));
        assertThat(isOwner(first)).isTrue();
        assertThat(isOwner(second)).isFalse();
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

    private long staffMembership(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now,
                now);
        return membershipOf(organizationId, accountId);
    }

    private long membershipOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    private boolean isOwner(long membershipId) {
        return jdbcTemplate.queryForObject(
                "select is_owner from company_memberships where id = ?", Boolean.class, membershipId);
    }
}
