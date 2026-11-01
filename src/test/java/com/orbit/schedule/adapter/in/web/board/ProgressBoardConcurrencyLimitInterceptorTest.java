package com.orbit.schedule.adapter.in.web.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.internal.exception.GlobalExceptionHandler;

/**
 * 동시 처리 상한은 조회를 상한만큼 쌓지 않고 상한 1에서 조회 하나를 유즈케이스 안에 붙잡아 둔 채 본다. 뒤따르는 인터셉터는 DB를 쓰는 가입 공통 검사 자리이며, 거절한
 * 조회가 거기까지 가지 않는지 센다.
 */
@DisplayName("진행 보드 조회 동시 처리 상한")
class ProgressBoardConcurrencyLimitInterceptorTest {

    private static final ProgressBoardInfo EMPTY_BOARD =
            new ProgressBoardInfo(List.of(), new ProgressBoardInfo.Summary(0, 0, 0, 0));

    private final AtomicInteger laterInterceptorCalls = new AtomicInteger();

    @Test
    @DisplayName("동시에 처리하는 조회가 상한이면 뒤 인터셉터로 넘기지 않고 Retry-After와 함께 503(COMMON-503)이며, 앞선 조회가 끝나면 다시 받는다")
    void rejectsBeforeLaterInterceptorsWhileConcurrentRequestsAreAtLimit() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MockMvc mockMvc = mockMvc(query -> {
            entered.countDown();
            await(release);
            return EMPTY_BOARD;
        });
        CompletableFuture<Integer> held = CompletableFuture.supplyAsync(() -> statusOf(mockMvc));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        requestBoard(mockMvc)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.code").value("COMMON-503"));
        assertThat(laterInterceptorCalls).hasValue(1);

        release.countDown();
        assertThat(held.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        requestBoard(mockMvc).andExpect(status().isOk());
    }

    @Test
    @DisplayName("조회가 오류로 끝나도 상한 자리를 돌려줘 다음 조회를 받는다")
    void releasesPermitWhenRequestFails() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        MockMvc mockMvc = mockMvc(query -> {
            if (failed.compareAndSet(false, true)) {
                throw new BusinessException(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
            }
            return EMPTY_BOARD;
        });

        requestBoard(mockMvc).andExpect(status().isForbidden());
        requestBoard(mockMvc).andExpect(status().isOk());
    }

    private MockMvc mockMvc(GetProgressBoardUseCase useCase) {
        HandlerInterceptor laterInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                laterInterceptorCalls.incrementAndGet();
                return true;
            }
        };
        return MockMvcBuilders.standaloneSetup(new ProgressBoardController(useCase))
                .addMappedInterceptors(
                        new String[] {ProgressBoardConcurrencyLimitInterceptor.PATH_PATTERN},
                        new ProgressBoardConcurrencyLimitInterceptor(new ProgressBoardProperties(1, null)),
                        laterInterceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /** 요청자는 요청을 보내는 스레드의 보안 문맥에서 읽으므로, 요청마다 그 스레드에 넣고 끝나면 지운다. */
    private static ResultActions requestBoard(MockMvc mockMvc) throws Exception {
        AccountPrincipal requester = () -> 1L;
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(requester, null, List.of()));
        try {
            return mockMvc.perform(get("/api/v1/organizations/{organizationId}/progress-board", 1));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static int statusOf(MockMvc mockMvc) {
        try {
            return requestBoard(mockMvc).andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
