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

import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Apple JWKS·토큰 엔드포인트를 로컬 스텁으로 바꾸고 검증기·HTTP 호출·Redis·PostgreSQL은 실제 경로로 지난다. 실제 Apple E2E는 아니다. */
@AutoConfigureMockMvc
@DisplayName("Apple iOS 로그인 API")
class AppleLoginApiIntegrationTest extends IntegrationTestSupport {

    private static final AppleAuthStub APPLE = AppleAuthStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppleRefreshTokenRepository refreshTokens;

    @DynamicPropertySource
    static void appleEndpoints(DynamicPropertyRegistry registry) {
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
        registry.add("app.auth.apple.token-api.token-uri", APPLE::tokenUri);
        registry.add("app.auth.apple.token-api.revoke-uri", APPLE::revokeUri);
        registry.add("app.auth.apple.client-secret.private-key", APPLE::clientSecretPrivateKeyPem);
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
    @DisplayName("처음 로그인하면 계정을 만들고 code를 교환해 refresh token을 보관하며, 다시 로그인하면 같은 계정을 쓴다")
    void logsInKeepsRefreshTokenAndReusesAccount() throws Exception {
        String subject = appleSubject();

        JsonNode first = readTree(login(APPLE.idToken(subject, hashed(issueNonce())), code(subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true))
                .andExpect(jsonPath("$.result.accessToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode second = readTree(login(APPLE.idToken(subject, hashed(issueNonce())), code(subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(accountId(second)).isEqualTo(accountId(first));
        assertThat(refreshTokens.listByAccount(new AccountId(accountId(first))))
                .singleElement()
                .extracting(AppleRefreshToken::clientId)
                .isEqualTo(AppleAuthStub.BUNDLE_ID);
        assertThat(APPLE.tokenRequests().getLast()).containsEntry("client_id", AppleAuthStub.BUNDLE_ID);
    }

    @Test
    @DisplayName("웹 Services ID를 대상으로 발급된 id_token은 iOS 로그인에서 받지 않는다")
    void rejectsIdTokenIssuedForWebServicesId() throws Exception {
        String subject = appleSubject();

        login(APPLE.idToken(subject, hashed(issueNonce()), AppleAuthStub.SERVICES_ID), code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-002"));
    }

    @Test
    @DisplayName("발급받은 Access Token으로 현재 계정을 조회한다")
    void readsCurrentAccountWithAccessToken() throws Exception {
        JsonNode login = loginAs(appleSubject(), AppleAuthStub.BUNDLE_ID);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + login.path("result")
                                                .path("accessToken")
                                                .asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(accountId(login)));
    }

    @Test
    @DisplayName("raw nonce를 해시하지 않고 그대로 실은 id_token은 거부한다")
    void rejectsRawNonceInIdToken() throws Exception {
        String subject = appleSubject();

        login(APPLE.idToken(subject, issueNonce()), code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("이미 사용한 nonce로 다시 로그인하면 거부한다")
    void rejectsReusedNonce() throws Exception {
        String subject = appleSubject();
        String nonce = hashed(issueNonce());
        login(APPLE.idToken(subject, nonce), code(subject)).andExpect(status().isOk());

        login(APPLE.idToken(subject, nonce), code(subject))
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
                .asString();
        String subject = appleSubject();

        login(APPLE.idToken(subject, hashed(kakaoNonce)), code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("Apple 키로 서명하지 않은 id_token은 거부한다")
    void rejectsForgedIdToken() throws Exception {
        String subject = appleSubject();

        login(APPLE.forgedIdToken(subject, hashed(issueNonce())), code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-002"));
    }

    @Test
    @DisplayName("이미 교환한 code를 다시 제출하면 AUTH-005다")
    void rejectsReusedAuthorizationCode() throws Exception {
        String subject = appleSubject();
        String code = code(subject);
        login(APPLE.idToken(subject, hashed(issueNonce())), code).andExpect(status().isOk());

        login(APPLE.idToken(subject, hashed(issueNonce())), code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("다른 사용자의 code를 함께 제출하면 AUTH-005이고 계정을 만들지 않는다")
    void rejectsAuthorizationCodeOfAnotherUser() throws Exception {
        String subject = appleSubject();

        login(APPLE.idToken(subject, hashed(issueNonce())), code(appleSubject()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-005"));

        login(APPLE.idToken(subject, hashed(issueNonce())), code(subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true));
    }

    @Test
    @DisplayName("Apple 토큰 엔드포인트가 응답하지 못하면 502 AUTH-006이다")
    void reportsAppleUnavailable() throws Exception {
        String subject = appleSubject();
        APPLE.failNextTokenRequests(1, 503, "{}");

        login(APPLE.idToken(subject, hashed(issueNonce())), code(subject))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH-006"));
    }

    @Test
    @DisplayName("id_token이나 authorization code가 비어 있으면 입력 오류다")
    void rejectsBlankProofs() throws Exception {
        String subject = appleSubject();

        login(" ", code(subject))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        login(APPLE.idToken(subject, hashed(issueNonce())), " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    private ResultActions login(String idToken, String authorizationCode) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AppleLoginRequest(idToken, authorizationCode))));
    }

    private JsonNode loginAs(String subject, String clientId) throws Exception {
        return readTree(login(APPLE.idToken(subject, hashed(issueNonce()), clientId), code(subject))
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
                .asString();
    }

    private static String code(String subject) {
        return APPLE.issueAuthorizationCode(subject);
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
