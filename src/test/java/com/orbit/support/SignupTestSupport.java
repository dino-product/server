package com.orbit.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 전체 API 통합 테스트에서 실제 HTTP API로 카카오 로그인과 가입 완료(프로필 입력·필수 약관 동의)를 마친다. 가입을 마치지 않은 계정의 인증 요청은 profile의
 * 공통 검사가 허용 목록 밖에서 PROFILE-005(403)로 막으므로, 다른 모듈의 API를 실제 Access Token으로 부르는 테스트는 {@link #signUpNewAccount}를 쓴다.
 * 카카오 id_token은 {@link KakaoJwksStub}이 서명하며 테스트 클래스가 그 JWKS 주소를 {@code app.auth.kakao.jwk-set-uri}로 등록해야 한다. 계정 식별자로
 * Access Token을 직접 서명하는 테스트는 {@link SignupOnce}로 그 계정의 가입을 마친다.
 */
public final class SignupTestSupport {

    private SignupTestSupport() {}

    /** 새 카카오 계정으로 로그인만 한다. 계정은 가입 미완료다. */
    public static TestAccount loginAsNewAccount(MockMvc mockMvc, ObjectMapper objectMapper, KakaoJwksStub kakao)
            throws Exception {
        JsonNode nonce = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String idToken = kakao.idToken(
                UUID.randomUUID().toString(), nonce.path("result").path("nonce").asText());
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("idToken", idToken))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return new TestAccount(
                login.path("result").path("accountId").asLong(),
                "Bearer " + login.path("result").path("accessToken").asText());
    }

    /** 새 카카오 계정으로 로그인하고 가입을 마친다. */
    public static TestAccount signUpNewAccount(MockMvc mockMvc, ObjectMapper objectMapper, KakaoJwksStub kakao)
            throws Exception {
        TestAccount account = loginAsNewAccount(mockMvc, objectMapper, kakao);
        completeSignup(mockMvc, objectMapper, account);
        return account;
    }

    /** 로그인한 계정의 프로필 입력과 필수 약관 동의를 마친다. */
    public static void completeSignup(MockMvc mockMvc, ObjectMapper objectMapper, TestAccount account)
            throws Exception {
        mockMvc.perform(put("/api/v1/profiles/{accountId}", account.accountId())
                        .header(HttpHeaders.AUTHORIZATION, account.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "테스트", "phoneNumber", "01012345678"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/profiles/{accountId}/terms-agreements", account.accountId())
                        .header(HttpHeaders.AUTHORIZATION, account.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("serviceTerms", true, "privacyPolicy", true))))
                .andExpect(status().isOk());
    }

    /** 로그인한 계정과 {@code Authorization} 헤더 값. */
    public record TestAccount(long accountId, String bearer) {}

    /**
     * 테스트가 계정 식별자로 직접 서명한 Access Token의 계정을 처음 쓸 때 한 번만 실제 API로 가입을 마친다. 테스트 인스턴스마다 하나 두며, 동시 요청
     * 테스트가 같은 계정의 토큰을 여러 스레드에서 만들어도 가입은 한 번만 부른다.
     */
    public static final class SignupOnce {

        private final ConcurrentMap<Long, Boolean> signedUp = new ConcurrentHashMap<>();

        public void ensure(MockMvc mockMvc, ObjectMapper objectMapper, long accountId, String accessToken) {
            signedUp.computeIfAbsent(accountId, id -> {
                try {
                    completeSignup(mockMvc, objectMapper, new TestAccount(id, "Bearer " + accessToken));
                } catch (Exception exception) {
                    throw new IllegalStateException("signup failed for account " + id, exception);
                }
                return Boolean.TRUE;
            });
        }
    }
}
