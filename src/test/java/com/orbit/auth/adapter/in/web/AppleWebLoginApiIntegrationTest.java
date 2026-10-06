package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleRefreshTokenRepository;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 웹·Android Apple 로그인. Apple 엔드포인트는 로컬 스텁이고 state·Redis·PostgreSQL·토큰 교환 경로는 실제 구현을 지난다. 실제 Apple E2E는 아니다. */
@AutoConfigureMockMvc
@DisplayName("Apple 웹·Android 로그인 API")
class AppleWebLoginApiIntegrationTest extends IntegrationTestSupport {

    private static final AppleAuthStub APPLE = AppleAuthStub.start();
    // RFC 7636 부록 B의 code_verifier와 그 S256 challenge
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    /** Apple이 첫 승인 때만 보내는 이름·이메일. 서버는 저장하지 않는다. */
    private static final String FIRST_AUTHORIZATION_USER =
            "{\"name\":{\"firstName\":\"길동\",\"lastName\":\"홍\"},\"email\":\"x@privaterelay.appleid.com\"}";

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
    @DisplayName("시작하면 Apple 인가 주소로 302 보내고 콜백 경로에만 쓰는 연결 쿠키를 남긴다")
    void redirectsToAppleWithBrowserBindingCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isFound())
                .andReturn();

        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        var query = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        assertThat(location.getHost()).isEqualTo("appleid.apple.com");
        assertThat(query.getFirst("client_id")).isEqualTo(AppleAuthStub.SERVICES_ID);
        assertThat(query.getFirst("response_mode")).isEqualTo("form_post");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("nonce")).matches("[0-9a-f]{64}");
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith(AppleWebLoginController.BROWSER_BINDING_COOKIE + "=")
                .contains("Path=/api/v1/auth/apple", "HttpOnly", "Secure", "SameSite=None", "Max-Age=600");
    }

    @Test
    @DisplayName("등록하지 않은 클라이언트로는 시작할 수 없다")
    void rejectsUnregisteredClient() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "https://evil.example")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/apple/authorize").param("code_challenge", CHALLENGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("무효한 토큰을 붙인 채로도 시작할 수 있다")
    void startsWithStaleTokenAttached() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "android")
                        .param("code_challenge", CHALLENGE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("웹: 콜백을 마치면 복귀 주소로 일회성 교환 코드를 보내고, 교환하면 Access Token을 받아 계정을 조회한다")
    void completesWebLoginThroughExchangeCode() throws Exception {
        String subject = appleSubject();
        Started started = start("web");

        MvcResult callback = callback(
                        started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                .andExpect(status().isFound())
                .andReturn();

        URI location = URI.create(callback.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(location.toString()).startsWith("http://localhost:3000/login/apple?code=");
        assertThat(callback.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        JsonNode login = readTree(exchange(query(location).getFirst("code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + login.path("result")
                                                .path("accessToken")
                                                .asString()))
                .andExpect(status().isOk());
        assertThat(refreshTokens.listByAccount(
                        new AccountId(login.path("result").path("accountId").asLong())))
                .extracting(AppleRefreshToken::clientId)
                .containsExactly(AppleAuthStub.SERVICES_ID);
        assertThat(APPLE.tokenRequests().getLast())
                .containsEntry("client_id", AppleAuthStub.SERVICES_ID)
                .containsEntry("redirect_uri", "http://localhost/api/v1/auth/apple/callback");
    }

    @Test
    @DisplayName("Android: 앱 스킴 복귀 주소로 교환 코드를 보낸다")
    void returnsToAndroidAppScheme() throws Exception {
        String subject = appleSubject();
        Started started = start("android");

        String location = callback(
                        started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        assertThat(location).startsWith("orbit-test://auth/apple?code=");
    }

    @Test
    @DisplayName("이미 처리한 state의 콜백은 복귀하지 않고 401 AUTH-003이다")
    void rejectsReusedState() throws Exception {
        String subject = appleSubject();
        Started started = start("web");
        String idToken = APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID);
        callback(started, idToken, code(subject)).andExpect(status().isFound());

        callback(started, idToken, code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("로그인을 시작하지 않은 브라우저의 유효한 콜백은 복귀하지 않고 401 AUTH-003이다")
    void rejectsValidCallbackFromAnotherBrowser() throws Exception {
        String subject = appleSubject();
        Started started = start("web");
        Started otherBrowser = new Started(started.state(), started.nonce(), "attacker-browser-binding");

        callback(otherBrowser, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
        mockMvc.perform(post("/api/v1/auth/apple/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("state", start("web").state())
                        .param("id_token", "x")
                        .param("code", "y"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    @DisplayName("id_token의 nonce가 이 로그인의 nonce가 아니면 error=AUTH-003으로 복귀한다")
    void returnsErrorForNonceMismatch() throws Exception {
        String subject = appleSubject();
        Started started = start("web");

        assertRedirectError(
                callback(
                        started,
                        APPLE.idToken(subject, HashedNonce.fromRaw("other").value(), AppleAuthStub.SERVICES_ID),
                        code(subject)),
                "AUTH-003");
    }

    @Test
    @DisplayName("iOS 클라이언트에 발급된 id_token으로는 웹 콜백을 마칠 수 없다(error=AUTH-002)")
    void returnsErrorForIdTokenOfAnotherClient() throws Exception {
        String subject = appleSubject();
        Started started = start("web");

        assertRedirectError(
                callback(started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.BUNDLE_ID), code(subject)),
                "AUTH-002");
    }

    @Test
    @DisplayName("사용자가 Apple 로그인을 취소하면 error=AUTH-008로 복귀한다")
    void returnsCancellation() throws Exception {
        Started started = start("web");

        assertRedirectError(
                mockMvc.perform(post("/api/v1/auth/apple/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(new Cookie(AppleWebLoginController.BROWSER_BINDING_COOKIE, started.binding()))
                        .param("state", started.state())
                        .param("error", "user_cancelled_authorize")),
                "AUTH-008");
    }

    @Test
    @DisplayName("Apple 토큰 엔드포인트가 응답하지 못하면 error=AUTH-006으로 복귀한다")
    void returnsErrorWhenAppleUnavailable() throws Exception {
        String subject = appleSubject();
        Started started = start("web");
        APPLE.failNextTokenRequests(1, 503, "{}");

        assertRedirectError(
                callback(started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject)),
                "AUTH-006");
    }

    @Test
    @DisplayName("교환 코드는 한 번만 쓸 수 있고 모르는 코드는 401 AUTH-007이다")
    void exchangesCodeOnlyOnce() throws Exception {
        String subject = appleSubject();
        Started started = start("web");
        URI location = URI.create(
                callback(started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.LOCATION));
        String code = query(location).getFirst("code");
        exchange(code).andExpect(status().isOk());

        exchange(code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
        exchange("unknown")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    @Test
    @DisplayName("PKCE S256 code_challenge 없이(또는 plain으로) 시작할 수 없다")
    void requiresS256CodeChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/authorize").param("client", "web"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE)
                        .param("code_challenge_method", "plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "web")
                        .param("code_challenge", "too-short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("복귀 주소의 교환 코드를 가로채도 시작한 클라이언트의 code_verifier 없이는 Access Token을 받을 수 없다")
    void rejectsInterceptedExchangeCodeWithoutVerifier() throws Exception {
        String subject = appleSubject();
        Started started = start("android");
        String location = callback(
                        started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);
        String intercepted = query(URI.create(location)).getFirst("code");

        exchange(intercepted, "attacker-own-verifier-attacker-own-verifier-x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
        exchange(intercepted)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    @Test
    @DisplayName("연결 쿠키 없는 위조 콜백은 정상 로그인의 state를 지우지 못한다")
    void keepsLegitimateStateAfterForgedCallback() throws Exception {
        String subject = appleSubject();
        Started started = start("web");
        String idToken = APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID);
        callback(new Started(started.state(), started.nonce(), "attacker-browser-binding"), idToken, code(subject))
                .andExpect(status().isUnauthorized());

        callback(started, idToken, code(subject)).andExpect(status().isFound()).andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.LOCATION))
                .contains("code="));
    }

    @Test
    @DisplayName("같은 Apple sub면 iOS 로그인과 웹 로그인이 같은 계정을 쓰고 클라이언트별 refresh token을 둔다")
    void sharesAccountBetweenIosAndWebLogins() throws Exception {
        String subject = appleSubject();
        String rawNonce = readTree(mockMvc.perform(post("/api/v1/auth/apple/nonces"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asString();
        long iosAccount = readTree(mockMvc.perform(post("/api/v1/auth/apple/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AppleLoginRequest(
                                        APPLE.idToken(
                                                subject,
                                                HashedNonce.fromRaw(rawNonce).value()),
                                        code(subject)))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("accountId")
                .asLong();

        Started started = start("web");
        URI location = URI.create(
                callback(started, APPLE.idToken(subject, started.nonce(), AppleAuthStub.SERVICES_ID), code(subject))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.LOCATION));
        exchange(query(location).getFirst("code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(iosAccount))
                .andExpect(jsonPath("$.result.registered").value(false));
        assertThat(refreshTokens.listByAccount(new AccountId(iosAccount)))
                .extracting(AppleRefreshToken::clientId)
                .containsExactlyInAnyOrder(AppleAuthStub.BUNDLE_ID, AppleAuthStub.SERVICES_ID);
    }

    private Started start(String client) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", client)
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isFound())
                .andReturn();
        MultiValueMap<String, String> query =
                query(URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION)));
        return new Started(
                query.getFirst("state"),
                query.getFirst("nonce"),
                result.getResponse()
                        .getCookie(AppleWebLoginController.BROWSER_BINDING_COOKIE)
                        .getValue());
    }

    private ResultActions callback(Started started, String idToken, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/apple/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(new Cookie(AppleWebLoginController.BROWSER_BINDING_COOKIE, started.binding()))
                .param("state", started.state())
                .param("code", code)
                .param("id_token", idToken)
                .param("user", FIRST_AUTHORIZATION_USER));
    }

    private ResultActions exchange(String code) throws Exception {
        return exchange(code, VERIFIER);
    }

    private ResultActions exchange(String code, String verifier) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/apple/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AppleLoginExchangeRequest(code, verifier))));
    }

    private static void assertRedirectError(ResultActions result, String errorCode) throws Exception {
        String location =
                result.andExpect(status().isFound()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(query(URI.create(location)).getFirst("error")).isEqualTo(errorCode);
        assertThat(query(URI.create(location)).containsKey("code")).isFalse();
    }

    private static MultiValueMap<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private static String code(String subject) {
        return APPLE.issueAuthorizationCode(subject);
    }

    private static String appleSubject() {
        return "001234." + UUID.randomUUID().toString().replace("-", "") + ".0123";
    }

    private JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }

    private record Started(String state, String nonce, String binding) {}
}
