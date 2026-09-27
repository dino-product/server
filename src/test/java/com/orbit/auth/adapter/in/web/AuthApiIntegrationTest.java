package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@DisplayName("인증 API")
class AuthApiIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void kakaoJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
    }

    @Test
    @DisplayName("토큰 없이 로그인 nonce를 발급받는다")
    void issuesLoginNonceWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.nonce").isString())
                .andExpect(jsonPath("$.result.expiresAt").isString());
    }

    @Test
    @DisplayName("유효한 id_token과 nonce로 처음 로그인하면 계정을 만들고, 다시 로그인하면 같은 계정을 쓴다")
    void logsInAndReusesAccount() throws Exception {
        String subject = UUID.randomUUID().toString();

        JsonNode first = login(KAKAO.idToken(subject, issueNonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.registered").value(true))
                .andExpect(jsonPath("$.result.accessToken").isString())
                .andExpect(jsonPath("$.result.accessTokenExpiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(this::readTree);
        JsonNode second = login(KAKAO.idToken(subject, issueNonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(this::readTree);

        assertThat(second.path("result").path("accountId").asLong())
                .isEqualTo(first.path("result").path("accountId").asLong());
    }

    @Test
    @DisplayName("이미 사용한 nonce로 다시 로그인하면 거부한다")
    void rejectsReusedNonce() throws Exception {
        String subject = UUID.randomUUID().toString();
        String nonce = issueNonce();
        login(KAKAO.idToken(subject, nonce)).andExpect(status().isOk());

        login(KAKAO.idToken(subject, nonce))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("서버가 발급하지 않은 nonce는 거부한다")
    void rejectsUnknownNonce() throws Exception {
        login(KAKAO.idToken(UUID.randomUUID().toString(), "client-made-nonce"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("카카오 키로 서명하지 않은 id_token은 거부한다")
    void rejectsForgedIdToken() throws Exception {
        login(KAKAO.forgedIdToken(UUID.randomUUID().toString(), issueNonce()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-002"));
    }

    @Test
    @DisplayName("id_token이 비어 있으면 입력 오류다")
    void rejectsBlankIdToken() throws Exception {
        login(" ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    private String issueNonce() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result.getResponse().getContentAsString())
                .path("result")
                .path("nonce")
                .asText();
    }

    private org.springframework.test.web.servlet.ResultActions login(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new KakaoLoginRequest(idToken))));
    }

    private JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }
}
