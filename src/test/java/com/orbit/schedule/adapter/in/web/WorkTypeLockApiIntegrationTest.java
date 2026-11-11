package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;

/**
 * 작업에 유형을 지정하는 schedule의 등록·수정과 작업 유형 삭제(organization, schedule의 시작 전 작업 해제 포함)가 유형 행의 공유 잠금으로 한 줄로 서는지 실제
 * 트랜잭션으로 확인한다. 먼저 시작한 쪽은 바깥 트랜잭션에 참여시켜 커밋을 늦추고, 그동안 들어온 반대쪽 요청이 기다리는지, 기다린 뒤 결과가 맞는지 본다.
 */
@DisplayName("작업 유형 지정과 삭제의 순서(PostgreSQL)")
class WorkTypeLockApiIntegrationTest extends ScheduleApiTestSupport {

    private static final long TIMEOUT_SECONDS = 10;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("유형을 지정한 등록이 커밋되기 전에 들어온 삭제는 기다렸다가 그 작업의 유형까지 비운다")
    void deletionWaitsForRegistrationThatChecked() throws Exception {
        Organization organization = organization();
        long workType = workType(organization, "설치");
        String owner = bearer(organization.owner());
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Integer> registration = inTransaction(() -> {
            int status = register(owner, organization.id(), workType);
            registered.countDown();
            await(commit);
            return status;
        });
        assertThat(registered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> deletion = CompletableFuture.supplyAsync(
                () -> status(() -> mockMvc.perform(deleteRequest(owner, organization.id(), workType))), executor);
        assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commit.countDown();
        assertThat(registration.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(deletion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(jdbcTemplate.queryForList(
                        "select job_type_id from work where company_id = ?", Long.class, organization.id()))
                .hasSize(1)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("삭제가 커밋되기 전에 들어온 등록은 기다렸다가 삭제된 유형이라 400 SCHEDULE-014이고 만들지 않는다")
    void registrationWaitsForDeletion() throws Exception {
        Organization organization = organization();
        long workType = workType(organization, "설치");
        String owner = bearer(organization.owner());
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Integer> deletion = inTransaction(() -> {
            int status = status(() -> mockMvc.perform(deleteRequest(owner, organization.id(), workType)));
            deleted.countDown();
            await(commit);
            return status;
        });
        assertThat(deleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> registration =
                CompletableFuture.supplyAsync(() -> register(owner, organization.id(), workType), executor);
        assertThatThrownBy(() -> registration.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commit.countDown();
        assertThat(deletion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(registration.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from work where company_id = ?", Integer.class, organization.id()))
                .isZero();
    }

    @Test
    @DisplayName("유형을 바꾸는 기본정보 수정이 커밋되기 전에 들어온 삭제는 기다렸다가 그 작업의 유형까지 비운다")
    void deletionWaitsForDetailsUpdateThatChecked() throws Exception {
        Organization organization = organization();
        long workType = workType(organization, "설치");
        long work = save(Work.register(
                new OrganizationId(organization.id()),
                "대기 작업",
                new MembershipId(organization.ownerMembership()),
                Instant.parse("2020-01-01T00:00:00Z"),
                null,
                new CustomerInfo(null, null, new SiteAddress(null, "서울시", null)),
                new PaymentInfo(null, null)));
        String owner = bearer(organization.owner());
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Integer> update = inTransaction(() -> {
            int status = status(() -> mockMvc.perform(
                    put("/api/v1/organizations/{organizationId}/works/{workId}", organization.id(), work)
                            .header(HttpHeaders.AUTHORIZATION, owner)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "보일러 점검", "workTypeId", workType)))));
            updated.countDown();
            await(commit);
            return status;
        });
        assertThat(updated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> deletion = CompletableFuture.supplyAsync(
                () -> status(() -> mockMvc.perform(deleteRequest(owner, organization.id(), workType))), executor);
        assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        commit.countDown();
        assertThat(update.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(deletion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject("select job_type_id from work where id = ?", Long.class, work))
                .isNull();
    }

    private int register(String bearer, long organizationId, long workType) {
        return status(() -> mockMvc.perform(post("/api/v1/organizations/{organizationId}/works", organizationId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "에어컨 설치", "workTypeId", workType)))));
    }

    private static MockHttpServletRequestBuilder deleteRequest(String bearer, long organizationId, long workType) {
        return delete("/api/v1/organizations/{organizationId}/work-types/{workTypeId}", organizationId, workType)
                .header(HttpHeaders.AUTHORIZATION, bearer);
    }

    /** 요청을 보내 응답 상태 코드를 돌려준다. */
    private int status(ThrowingRequest request) {
        try {
            return request.send().andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Map<String, ?> body) {
        return objectMapper.writeValueAsString(body);
    }

    private <T> CompletableFuture<T> inTransaction(Supplier<T> body) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return CompletableFuture.supplyAsync(() -> transaction.execute(status -> body.get()), executor);
    }

    @FunctionalInterface
    private interface ThrowingRequest {
        ResultActions send() throws Exception;
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
