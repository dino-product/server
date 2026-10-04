package com.orbit.auth.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.orbit.support.IntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("Apple 로그인 API")
class AppleLoginApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이 Apple 로그인 raw nonce를 발급받는다")
    void issuesAppleLoginNonceWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/apple/nonces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.nonce").isString())
                .andExpect(jsonPath("$.result.expiresAt").isString());
    }

    @Test
    @DisplayName("무효한 토큰을 붙인 채로도 Apple nonce를 발급받는다")
    void issuesAppleLoginNonceWithStaleTokenAttached() throws Exception {
        mockMvc.perform(post("/api/v1/auth/apple/nonces").header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nonce").isString());
    }
}
