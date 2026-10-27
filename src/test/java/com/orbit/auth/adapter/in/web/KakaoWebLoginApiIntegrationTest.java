package com.orbit.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.orbit.auth.AccountClosure;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.KakaoJwksStub;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 웹 카카오 로그인. 카카오 JWKS·토큰 엔드포인트는 로컬 스텁이고 state·Redis·PostgreSQL·토큰 교환·id_token 검증 경로는 실제 구현을 지난다. 실제 카카오 E2E는
 * 아니다.
 */
@DisplayName("카카오 웹 로그인 API")
class KakaoWebLoginApiIntegrationTest extends AuthApiTestSupport {

    // RFC 7636 부록 B의 code_verifier와 그 S256 challenge
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String RETURN_URI = "http://localhost:3000/login/kakao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountClosure accountClosure;

    @Test
    @DisplayName("시작하면 REST API 키·openid 범위로 카카오 인가 주소로 302 보내고 카카오 경로에만 쓰는 Lax 연결 쿠키를 남긴다")
    void redirectsToKakaoWithBrowserBindingCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isFound())
                .andReturn();

        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(location)
                .hasHost("kauth.kakao.com")
                .hasPath("/oauth/authorize")
                .hasParameter("response_type", "code")
                .hasParameter("client_id", KakaoJwksStub.REST_API_KEY)
                .hasParameter("redirect_uri", "http://localhost/api/v1/auth/kakao/callback")
                .hasParameter("scope", "openid");
        assertThat(query(location).getFirst("state")).isNotBlank();
        assertThat(query(location).getFirst("nonce")).matches("[0-9a-f]{64}");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(KakaoWebLoginController.BROWSER_BINDING_COOKIE + "=")
                .contains("Path=/api/v1/auth/kakao", "HttpOnly", "Secure", "SameSite=Lax", "Max-Age=600");
    }

    @Test
    @DisplayName("등록하지 않은 클라이언트나 S256이 아닌 PKCE로는 시작할 수 없다")
    void rejectsUnregisteredClientAndInvalidChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "android")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/kakao/authorize").param("code_challenge", CHALLENGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE)
                        .param("code_challenge_method", "plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "web")
                        .param("code_challenge", "too-short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("무효한 토큰을 붙인 채로도 시작할 수 있다")
    void startsWithStaleTokenAttached() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("콜백을 마치면 토큰 없이 일회성 교환 코드로 복귀하고, 교환하면 Access Token·refresh token을 받아 계정을 조회한다")
    void completesWebLoginThroughExchangeCode() throws Exception {
        Started started = start();

        MvcResult callback = callback(started, KAKAO.issueAuthorizationCode(kakaoSubject(), started.nonce()))
                .andExpect(status().isFound())
                .andReturn();

        URI location = URI.create(callback.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(location.toString()).startsWith(RETURN_URI + "?code=");
        assertThat(query(location).keySet()).containsExactly("code");
        assertThat(callback.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        JsonNode login = readTree(exchange(query(location).getFirst("code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.registered").value(true))
                .andExpect(jsonPath("$.result.refreshToken").isString())
                .andExpect(jsonPath("$.result.refreshTokenExpiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String accessToken = login.path("result").path("accessToken").asString();
        assertThat(location.toString()).doesNotContain(accessToken);
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId")
                        .value(login.path("result").path("accountId").asLong()));
        assertThat(KAKAO.tokenRequests().getLast())
                .containsEntry("client_id", KakaoJwksStub.REST_API_KEY)
                .containsEntry("client_secret", KakaoJwksStub.CLIENT_SECRET)
                .containsEntry("redirect_uri", "http://localhost/api/v1/auth/kakao/callback");
    }

    @Test
    @DisplayName("같은 카카오 회원번호면 앱 SDK 로그인과 웹 로그인이 같은 계정을 쓴다")
    void sharesAccountBetweenAppAndWebLogins() throws Exception {
        String subject = kakaoSubject();
        String nonce = readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asString();
        long appAccount = readTree(mockMvc.perform(post("/api/v1/auth/kakao/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new KakaoLoginRequest(KAKAO.idToken(subject, nonce)))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("accountId")
                .asLong();

        exchange(webLoginCode(subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(appAccount))
                .andExpect(jsonPath("$.result.registered").value(false));
    }

    @Test
    @DisplayName("이미 처리한 state의 콜백은 복귀하지 않고 401 AUTH-003이며 카카오를 다시 부르지 않는다")
    void rejectsReusedState() throws Exception {
        Started started = start();
        callback(started, KAKAO.issueAuthorizationCode(kakaoSubject(), started.nonce()))
                .andExpect(status().isFound());
        int tokenRequests = KAKAO.tokenRequests().size();

        callback(started, KAKAO.issueAuthorizationCode(kakaoSubject(), started.nonce()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
        assertThat(KAKAO.tokenRequests()).hasSize(tokenRequests);
    }

    @Test
    @DisplayName("로그인을 시작하지 않은 브라우저의 유효한 콜백은 계정을 만들지 않고 401 AUTH-003이며, 정상 로그인의 state도 지우지 못한다")
    void rejectsValidCallbackFromAnotherBrowser() throws Exception {
        Started started = start();
        String code = KAKAO.issueAuthorizationCode(kakaoSubject(), started.nonce());
        int tokenRequests = KAKAO.tokenRequests().size();

        callback(new Started(started.state(), started.nonce(), "attacker-browser-binding"), code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("state", started.state())
                        .param("code", code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
        assertThat(KAKAO.tokenRequests()).hasSize(tokenRequests);

        callback(started, code).andExpect(status().isFound()).andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.LOCATION))
                .startsWith(RETURN_URI + "?code="));
    }

    @Test
    @DisplayName("사용자가 카카오 동의 화면에서 취소하면 error=AUTH-008로만 복귀한다")
    void returnsCancellation() throws Exception {
        Started started = start();

        assertRedirectError(
                mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(new Cookie(KakaoWebLoginController.BROWSER_BINDING_COOKIE, started.binding()))
                        .param("state", started.state())
                        .param("error", "access_denied")
                        .param("error_description", "User denied access")),
                "AUTH-008");
    }

    @Test
    @DisplayName("카카오가 인가 코드를 거절하면 error=AUTH-002, 카카오 토큰 엔드포인트가 응답하지 못하면 AUTH-010으로 복귀한다")
    void returnsErrorsForTokenEndpointFailures() throws Exception {
        assertRedirectError(callback(start(), "kakao-code-never-issued"), "AUTH-002");

        Started started = start();
        KAKAO.failNextTokenRequests(1, 503, "{}");
        assertRedirectError(
                callback(started, KAKAO.issueAuthorizationCode(kakaoSubject(), started.nonce())), "AUTH-010");
    }

    @Test
    @DisplayName("토큰 엔드포인트가 준 id_token이 JWKS로 검증되지 않으면 AUTH-010, nonce가 이 로그인의 것이 아니면 AUTH-003으로 복귀한다")
    void returnsErrorsForInvalidIdToken() throws Exception {
        Started forged = start();
        assertRedirectError(
                callback(forged, KAKAO.issueAuthorizationCodeWithForgedIdToken(kakaoSubject(), forged.nonce())),
                "AUTH-010");

        assertRedirectError(
                callback(
                        start(),
                        KAKAO.issueAuthorizationCode(
                                kakaoSubject(), HashedNonce.fromRaw("other").value())),
                "AUTH-003");
    }

    @Test
    @DisplayName("교환 코드는 한 번만 쓸 수 있고, 모르는 코드·시작한 클라이언트의 것이 아닌 code_verifier는 401 AUTH-007이다")
    void exchangesCodeOnlyOnceWithMatchingVerifier() throws Exception {
        String code = webLoginCode(kakaoSubject());
        exchange(code).andExpect(status().isOk());

        exchange(code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
        exchange("unknown")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));

        String intercepted = webLoginCode(kakaoSubject());
        exchange(intercepted, "attacker-own-verifier-attacker-own-verifier-x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
        exchange(intercepted)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    @Test
    @DisplayName("교환 코드를 만든 뒤 계정이 닫히면 그 코드로 닫힌 계정의 토큰을 받을 수 없다")
    void rejectsExchangeAfterAccountClosure() throws Exception {
        String subject = kakaoSubject();
        long accountId = readTree(exchange(webLoginCode(subject))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("accountId")
                .asLong();
        String pendingCode = webLoginCode(subject);

        accountClosure.close(accountId);

        exchange(pendingCode)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    @Test
    @DisplayName("Apple 웹 로그인의 교환 코드로는 카카오 교환 경로에서 토큰을 받을 수 없다")
    void rejectsExchangeCodeOfAnotherProvider() throws Exception {
        String appleCode = appleWebLoginCode();

        exchange(appleCode)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    private Started start() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE))
                .andExpect(status().isFound())
                .andReturn();
        MultiValueMap<String, String> query =
                query(URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION)));
        return new Started(
                query.getFirst("state"),
                query.getFirst("nonce"),
                result.getResponse()
                        .getCookie(KakaoWebLoginController.BROWSER_BINDING_COOKIE)
                        .getValue());
    }

    private ResultActions callback(Started started, String code) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/kakao/callback")
                .cookie(new Cookie(KakaoWebLoginController.BROWSER_BINDING_COOKIE, started.binding()))
                .param("state", started.state())
                .param("code", code));
    }

    /** 웹 로그인을 시작하고 콜백까지 마쳐 복귀 주소의 교환 코드를 돌려준다. */
    private String webLoginCode(String subject) throws Exception {
        Started started = start();
        URI location = URI.create(callback(started, KAKAO.issueAuthorizationCode(subject, started.nonce()))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION));
        return query(location).getFirst("code");
    }

    private String appleWebLoginCode() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/v1/auth/apple/authorize")
                        .param("client", "web")
                        .param("code_challenge", CHALLENGE))
                .andReturn();
        MultiValueMap<String, String> query =
                query(URI.create(started.getResponse().getHeader(HttpHeaders.LOCATION)));
        String subject = "001234." + UUID.randomUUID().toString().replace("-", "") + ".0123";
        URI location = URI.create(mockMvc.perform(post("/api/v1/auth/apple/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(started.getResponse().getCookie(AppleWebLoginController.BROWSER_BINDING_COOKIE))
                        .param("state", query.getFirst("state"))
                        .param("code", APPLE.issueAuthorizationCode(subject))
                        .param("id_token", APPLE.idToken(subject, query.getFirst("nonce"), AppleAuthStub.SERVICES_ID)))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION));
        return query(location).getFirst("code");
    }

    private ResultActions exchange(String code) throws Exception {
        return exchange(code, VERIFIER);
    }

    private ResultActions exchange(String code, String verifier) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new KakaoLoginExchangeRequest(code, verifier))));
    }

    private static void assertRedirectError(ResultActions result, String errorCode) throws Exception {
        MvcResult response = result.andExpect(status().isFound()).andReturn();
        URI location = URI.create(response.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(location.toString()).startsWith(RETURN_URI + "?");
        assertThat(query(location).getFirst("error")).isEqualTo(errorCode);
        assertThat(query(location).containsKey("code")).isFalse();
        assertThat(response.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    private static MultiValueMap<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private static String kakaoSubject() {
        return String.valueOf(
                4_000_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000L));
    }

    private JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }

    private record Started(String state, String nonce, String binding) {}
}
