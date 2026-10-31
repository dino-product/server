package com.orbit.shared.internal.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLTransientConnectionException;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 트랜잭션을 시작하다 실패한 경우는 실제 커넥션 풀을 고갈시키지 않고 JPA 트랜잭션 관리자가 던지는 예외 사슬을 그대로 만들어 본다. */
@DisplayName("전역 오류 응답")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("DB 커넥션을 제때 얻지 못하면 Retry-After와 함께 503(COMMON-503)이다")
    void tellsWhenToRetryWhenDatabaseConnectionIsUnavailable() throws Exception {
        mockMvc(new CannotCreateTransactionException(
                        "Could not open JPA EntityManager for transaction",
                        new JDBCConnectionException(
                                "Unable to acquire JDBC Connection",
                                new SQLTransientConnectionException("HikariPool-1 - Connection is not available"))))
                .perform(get("/failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.code").value("COMMON-503"));
    }

    @Test
    @DisplayName("커넥션 대기 초과가 아닌 트랜잭션 시작 실패는 Retry-After 없는 500(COMMON-500)이다")
    void treatsOtherTransactionStartFailureAsServerError() throws Exception {
        mockMvc(new CannotCreateTransactionException(
                        "Could not open JPA EntityManager for transaction", new IllegalStateException("broken")))
                .perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("COMMON-500"));
    }

    private static MockMvc mockMvc(RuntimeException failure) {
        return MockMvcBuilders.standaloneSetup(new FailingController(failure))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class FailingController {

        private final RuntimeException failure;

        FailingController(RuntimeException failure) {
            this.failure = failure;
        }

        @GetMapping("/failure")
        void fail() {
            throw failure;
        }
    }
}
