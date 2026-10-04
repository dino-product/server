package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    @DynamicPropertySource
    static void kakaoJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
    }

    @AfterAll
    static void stopKakaoJwks() {
        KAKAO.close();
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

    @Test
    @DisplayName("발급받은 Access Token으로 현재 계정을 조회한다")
    void readsCurrentAccountWithAccessToken() throws Exception {
        JsonNode login = loginAsNewAccount();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.accountId")
                        .value(login.path("result").path("accountId").asLong()))
                .andExpect(jsonPath("$.result.registeredAt").isString())
                .andExpect(jsonPath("$.result.accessTokenExpiresAt")
                        .value(login.path("result").path("accessTokenExpiresAt").asText()));
    }

    @Test
    @DisplayName("토큰 없이 보호 자원에 접근하면 401이다")
    void requiresTokenForProtectedResource() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    @DisplayName("위조된 토큰으로 접근하면 자원이 없는 것과 같은 404다")
    void hidesResourceFromTamperedToken() throws Exception {
        String token = bearer(loginAsNewAccount());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertNotFoundLikeMissingResource(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, tampered));
    }

    @Test
    @DisplayName("만료된 토큰으로 접근하면 자원이 없는 것과 같은 404다")
    void hidesResourceFromExpiredToken() throws Exception {
        assertNotFoundLikeMissingResource(
                get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken()));
    }

    @Test
    @DisplayName("로그아웃한 토큰으로 접근하면 자원이 없는 것과 같은 404다")
    void hidesResourceFromLoggedOutToken() throws Exception {
        String token = bearer(loginAsNewAccount());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        assertNotFoundLikeMissingResource(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token));
        assertNotFoundLikeMissingResource(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    @DisplayName("로그아웃한 토큰을 여전히 붙인 채로도 nonce 발급과 로그인은 된다")
    void allowsReloginWithStaleTokenAttached() throws Exception {
        String stale = bearer(loginAsNewAccount());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, stale))
                .andExpect(status().isNoContent());

        MvcResult nonce = mockMvc.perform(post("/api/v1/auth/kakao/nonces").header(HttpHeaders.AUTHORIZATION, stale))
                .andExpect(status().isOk())
                .andReturn();
        String nonceValue = readTree(nonce.getResponse().getContentAsString())
                .path("result")
                .path("nonce")
                .asText();
        mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .header(HttpHeaders.AUTHORIZATION, stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest(
                                KAKAO.idToken(UUID.randomUUID().toString(), nonceValue)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃은 해당 토큰만 폐기하고 같은 계정의 다른 토큰은 유지한다")
    void revokesOnlyTheLoggedOutToken() throws Exception {
        String subject = UUID.randomUUID().toString();
        String first = bearer(loginAs(subject));
        String second = bearer(loginAs(subject));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, second))
                .andExpect(status().isOk());
    }

    private void assertNotFoundLikeMissingResource(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        String hidden = mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missing = mockMvc.perform(
                        get("/api/v1/no-such-resource").header(HttpHeaders.AUTHORIZATION, bearer(loginAsNewAccount())))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(hidden).isEqualTo(missing);
        assertThat(readTree(hidden).path("code").asText()).isEqualTo("COMMON-404");
    }

    private JsonNode loginAsNewAccount() throws Exception {
        return loginAs(UUID.randomUUID().toString());
    }

    private JsonNode loginAs(String subject) throws Exception {
        return login(KAKAO.idToken(subject, issueNonce()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(this::readTree);
    }

    private static String bearer(JsonNode login) {
        return "Bearer " + login.path("result").path("accessToken").asText();
    }

    private String expiredAccessToken() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(7200);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer(jwtIssuer)
                        .subject("1")
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(issuedAt.plusSeconds(3600)))
                        .claim("token_use", "access")
                        .build());
        jwt.sign(new MACSigner(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return jwt.serialize();
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
