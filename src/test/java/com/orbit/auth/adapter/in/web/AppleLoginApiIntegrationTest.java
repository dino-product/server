package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@DisplayName("Apple 로그인 API")
class AppleLoginApiIntegrationTest extends IntegrationTestSupport {

    private static final AppleAuthStub APPLE = AppleAuthStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void appleJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
    }

    @AfterAll
    static void stopStub() {
        APPLE.close();
    }

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

    @Test
    @DisplayName("해시 nonce가 실린 id_token으로 처음 로그인하면 계정을 만들고, 다시 로그인하면 같은 계정을 쓴다")
    void logsInAndReusesAccount() throws Exception {
        String subject = appleSubject();

        JsonNode first = login(APPLE.idToken(subject, hashed(issueNonce())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true))
                .andExpect(jsonPath("$.result.accessToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(objectMapper::readTree);
        JsonNode second = login(APPLE.idToken(subject, hashed(issueNonce())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(objectMapper::readTree);

        assertThat(accountId(second)).isEqualTo(accountId(first));
    }

    @Test
    @DisplayName("허용 목록의 다른 클라이언트(Services ID)로 받은 id_token도 같은 sub면 같은 계정이다")
    void sharesAccountAcrossAllowedClientsWithSameSubject() throws Exception {
        String subject = appleSubject();
        JsonNode ios = loginBody(APPLE.idToken(subject, hashed(issueNonce())));

        JsonNode web = loginBody(APPLE.idToken(subject, hashed(issueNonce()), AppleAuthStub.SERVICES_ID));

        assertThat(accountId(web)).isEqualTo(accountId(ios));
    }

    @Test
    @DisplayName("발급받은 Access Token으로 현재 계정을 조회한다")
    void readsCurrentAccountWithAccessToken() throws Exception {
        JsonNode login = loginBody(APPLE.idToken(appleSubject(), hashed(issueNonce())));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + login.path("result")
                                                .path("accessToken")
                                                .asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(accountId(login)));
    }

    @Test
    @DisplayName("raw nonce를 해시하지 않고 그대로 실은 id_token은 거부한다")
    void rejectsRawNonceInIdToken() throws Exception {
        login(APPLE.idToken(appleSubject(), issueNonce()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("이미 사용한 nonce로 다시 로그인하면 거부한다")
    void rejectsReusedNonce() throws Exception {
        String subject = appleSubject();
        String nonce = hashed(issueNonce());
        login(APPLE.idToken(subject, nonce)).andExpect(status().isOk());

        login(APPLE.idToken(subject, nonce))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("카카오 로그인용으로 발급한 nonce로는 Apple 로그인할 수 없다")
    void rejectsNonceIssuedForKakao() throws Exception {
        String kakaoNonce = readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asText();

        login(APPLE.idToken(appleSubject(), hashed(kakaoNonce)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("Apple 키로 서명하지 않은 id_token은 거부한다")
    void rejectsForgedIdToken() throws Exception {
        login(APPLE.forgedIdToken(appleSubject(), hashed(issueNonce())))
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

    private ResultActions login(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AppleLoginRequest(idToken))));
    }

    private JsonNode loginBody(String idToken) throws Exception {
        return readTree(login(idToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String issueNonce() throws Exception {
        return readTree(mockMvc.perform(post("/api/v1/auth/apple/nonces"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asText();
    }

    private static String hashed(String rawNonce) {
        return HashedNonce.fromRaw(rawNonce).value();
    }

    private static String appleSubject() {
        return "001234." + UUID.randomUUID().toString().replace("-", "") + ".0123";
    }

    private static long accountId(JsonNode login) {
        return login.path("result").path("accountId").asLong();
    }

    private JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }
}
