package com.orbit.auth.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.orbit.support.IntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("인증 API")
class AuthApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이 로그인 nonce를 발급받는다")
    void issuesLoginNonceWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.nonce").isString())
                .andExpect(jsonPath("$.result.expiresAt").isString());
    }
}
