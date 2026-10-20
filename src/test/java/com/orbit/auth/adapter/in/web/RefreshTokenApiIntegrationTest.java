package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("토큰 재발급 API")
class RefreshTokenApiIntegrationTest extends AuthApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("refresh token으로 새 Access Token과 다른 refresh token을 받고, 새 Access Token으로 보호 자원을 쓴다")
    void rotatesRefreshToken() throws Exception {
        JsonNode login = login();
        String first = refreshToken(login);

        JsonNode refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isString())
                .andExpect(jsonPath("$.result.accessTokenExpiresAt").isString())
                .andExpect(jsonPath("$.result.refreshTokenExpiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(objectMapper::readTree);

        String second = refreshToken(refreshed);
        assertThat(second).isNotEqualTo(first).startsWith(first.substring(0, 37));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId")
                        .value(login.path("result").path("accountId").asLong()));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 refresh token으로 동시에 재발급해도 둘 다 성공하고 같은 다음 refresh token을 받는다")
    void sameNextTokenForConcurrentRefresh() throws Exception {
        String first = refreshToken(login());
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> call = () -> {
                ready.await();
                return refresh(first)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
            };
            List<Future<String>> results = List.of(executor.submit(call), executor.submit(call));
            ready.countDown();

            String one = refreshToken(objectMapper.readTree(results.get(0).get(30, TimeUnit.SECONDS)));
            String other = refreshToken(objectMapper.readTree(results.get(1).get(30, TimeUnit.SECONDS)));

            assertThat(one).isEqualTo(other).isNotEqualTo(first);
            refresh(one).andExpect(status().isOk());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("회전 직후 직전 refresh token을 다시 내면 세션을 두고 같은 다음 토큰을 돌려준다")
    void replaysPreviousTokenWithinGrace() throws Exception {
        String first = refreshToken(login());
        String second = refreshToken(readBody(refresh(first).andExpect(status().isOk())));

        String replayed = refreshToken(readBody(refresh(first).andExpect(status().isOk())));

        assertThat(replayed).isEqualTo(second);
        refresh(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("유예가 지난 뒤 이미 쓴 refresh token이 오면 그 기기 세션을 철회해 최신 refresh token도 거절한다")
    void revokesSessionOnReuseAfterGrace() throws Exception {
        String first = refreshToken(login());
        String second = refreshToken(readBody(refresh(first).andExpect(status().isOk())));
        jdbcTemplate.update(
                "update refresh_sessions set rotated_at = rotated_at - interval '31 seconds' where id = ?",
                sessionId(first));

        assertRejected(refresh(first));

        assertRejected(refresh(second));
        assertThat(sessionCount(first)).isZero();
    }

    @Test
    @DisplayName("미사용 수명이 지난 refresh token은 거절하고 세션을 지운다")
    void rejectsExpiredSession() throws Exception {
        String first = refreshToken(login());
        jdbcTemplate.update(
                "update refresh_sessions set started_at = now() - interval '31 days',"
                        + " expires_at = now() - interval '1 second' where id = ?",
                sessionId(first));

        assertRejected(refresh(first));

        assertThat(sessionCount(first)).isZero();
    }

    @Test
    @DisplayName("형식이 어긋나거나 없는 세션의 refresh token은 이유를 드러내지 않고 같은 오류로 거절한다")
    void rejectsUnknownOrMalformedTokenAlike() throws Exception {
        String unknown = UUID.randomUUID() + "." + "a".repeat(43);

        String unknownBody = assertRejected(refresh(unknown));
        String malformedBody = assertRejected(refresh("not-a-refresh-token"));

        assertThat(malformedBody).isEqualTo(unknownBody);
    }

    @Test
    @DisplayName("refresh token이 비어 있으면 입력 오류다")
    void rejectsBlankToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("만료·위조된 Access Token이 함께 붙어 와도 재발급한다")
    void refreshesWithStaleAccessTokenAttached() throws Exception {
        String first = refreshToken(login());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-or-expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokensRequest(first))))
                .andExpect(status().isOk());
    }

    private String assertRejected(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-009"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Integer sessionCount(String refreshToken) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where id = ?", Integer.class, sessionId(refreshToken));
    }

    private static String sessionId(String refreshToken) {
        return refreshToken.substring(0, 36);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokensRequest(refreshToken))));
    }

    private JsonNode login() throws Exception {
        String nonce = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asText();
        return readBody(mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest(
                                KAKAO.idToken(UUID.randomUUID().toString(), nonce)))))
                .andExpect(status().isOk()));
    }

    private JsonNode readBody(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String refreshToken(JsonNode body) {
        return body.path("result").path("refreshToken").asText();
    }

    private static String accessToken(JsonNode body) {
        return body.path("result").path("accessToken").asText();
    }
}
