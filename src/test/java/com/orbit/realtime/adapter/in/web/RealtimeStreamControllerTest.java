package com.orbit.realtime.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.orbit.auth.AccountPrincipal;
import com.orbit.realtime.application.error.RealtimeErrorCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.internal.exception.GlobalExceptionHandler;

/** 인스턴스가 연결을 더 받을 수 없는 경우는 연결 2,000개를 채우지 않고 유즈케이스를 대체해 본다. */
@DisplayName("실시간 갱신 연결 응답")
class RealtimeStreamControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RealtimeStreamController(
                    command -> {
                        throw new BusinessException(RealtimeErrorCode.CONNECTION_UNAVAILABLE);
                    },
                    new RealtimeStreamProperties(null, null)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("연결을 받을 수 없으면 Retry-After와 함께 JSON 503(REALTIME-002)이다")
    void tellsWhenToRetryWhenConnectionsAreUnavailable() throws Exception {
        AccountPrincipal requester = () -> 1L;
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(requester, null, List.of()));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/events", 1)
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("REALTIME-002"));
    }

    @Test
    @DisplayName("이벤트 스트림을 받지 않겠다는 요청은 본문 없이 406이다")
    void rejectsRequestThatDoesNotAcceptEventStream() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/events", 1)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));
    }
}
