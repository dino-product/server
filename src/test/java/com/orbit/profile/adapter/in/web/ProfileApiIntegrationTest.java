package com.orbit.profile.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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

import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@DisplayName("프로필 API")
class ProfileApiIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void kakaoJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
    }

    @AfterAll
    static void stopKakaoJwks() {
        KAKAO.close();
    }

    @Test
    @DisplayName("카카오 인증 직후 계정은 가입 미완료이고 다음 단계는 프로필 입력이다")
    void newAccountStartsWithProfileStep() throws Exception {
        LoggedIn account = loginAsNewAccount();

        getProfile(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(account.accountId()))
                .andExpect(jsonPath("$.result.status").value("PENDING_SIGNUP"))
                .andExpect(jsonPath("$.result.nextStep").value("PROFILE"))
                .andExpect(jsonPath("$.result.name").doesNotExist())
                .andExpect(jsonPath("$.result.phoneNumber").doesNotExist());
    }

    @Test
    @DisplayName("프로필을 입력하면 약관 동의가 다음 단계이고, 다시 조회하면 입력해 둔 값이 남아 있다")
    void savesProfileAndResumesFromIt() throws Exception {
        LoggedIn account = loginAsNewAccount();

        saveProfile(account, account.accountId(), " 홍길동 ", "010-1234-5678")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING_SIGNUP"))
                .andExpect(jsonPath("$.result.nextStep").value("TERMS"))
                .andExpect(jsonPath("$.result.name").value("홍길동"))
                .andExpect(jsonPath("$.result.phoneNumber").value("01012345678"));

        getProfile(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nextStep").value("TERMS"))
                .andExpect(jsonPath("$.result.name").value("홍길동"))
                .andExpect(jsonPath("$.result.phoneNumber").value("01012345678"));
    }

    @Test
    @DisplayName("이름·연락처 규칙을 어기면 PROFILE-001로 거부한다")
    void rejectsInvalidProfile() throws Exception {
        LoggedIn account = loginAsNewAccount();

        saveProfile(account, account.accountId(), "홍길동😀", "01012345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE-001"));
        saveProfile(account, account.accountId(), "홍길동", "011-1234-5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE-001"));
        saveProfile(account, account.accountId(), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE-001"));
    }

    @Test
    @DisplayName("다른 계정의 프로필은 조회·입력 모두 PROFILE-002로 거부한다")
    void hidesOtherAccountsProfile() throws Exception {
        LoggedIn account = loginAsNewAccount();
        LoggedIn other = loginAsNewAccount();

        mockMvc.perform(get("/api/v1/profiles/{accountId}", other.accountId())
                        .header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE-002"));
        saveProfile(account, other.accountId(), "홍길동", "01012345678")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE-002"));
    }

    @Test
    @DisplayName("프로필 입력 뒤 필수 약관에 동의하면 가입 완료가 되고 가입일과 마케팅 선택이 남는다")
    void completesSignupWithRequiredTerms() throws Exception {
        LoggedIn account = loginAsNewAccount();
        saveProfile(account, account.accountId(), "홍길동", "01012345678").andExpect(status().isOk());

        agreeToTerms(account, account.accountId(), true, true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.nextStep").value("COMPLETED"))
                .andExpect(jsonPath("$.result.signedUpAt").isString())
                .andExpect(jsonPath("$.result.marketingAgreed").value(true));
        getProfile(account)
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.nextStep").value("COMPLETED"));
    }

    @Test
    @DisplayName("프로필 입력 전 약관 동의는 PROFILE-003, 필수 약관 미동의는 PROFILE-004로 거부하고 가입 미완료로 남긴다")
    void rejectsTermsOutOfOrderOrIncomplete() throws Exception {
        LoggedIn account = loginAsNewAccount();

        agreeToTerms(account, account.accountId(), true, true, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE-003"));
        saveProfile(account, account.accountId(), "홍길동", "01012345678").andExpect(status().isOk());
        agreeToTerms(account, account.accountId(), true, null, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE-004"));
        getProfile(account)
                .andExpect(jsonPath("$.result.status").value("PENDING_SIGNUP"))
                .andExpect(jsonPath("$.result.nextStep").value("TERMS"));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/{accountId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private ResultActions getProfile(LoggedIn account) throws Exception {
        return mockMvc.perform(get("/api/v1/profiles/{accountId}", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private ResultActions saveProfile(LoggedIn account, long accountId, String name, String phoneNumber)
            throws Exception {
        return mockMvc.perform(put("/api/v1/profiles/{accountId}", accountId)
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SaveProfileRequest(name, phoneNumber))));
    }

    private ResultActions agreeToTerms(
            LoggedIn account, long accountId, Boolean serviceTerms, Boolean privacyPolicy, Boolean marketing)
            throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/{accountId}/terms-agreements", accountId)
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AgreeToTermsRequest(serviceTerms, privacyPolicy, marketing))));
    }

    private LoggedIn loginAsNewAccount() throws Exception {
        String nonce = readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asText();
        String idToken = KAKAO.idToken(UUID.randomUUID().toString(), nonce);
        JsonNode login = readTree(mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("idToken", idToken))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return new LoggedIn(
                login.path("result").path("accountId").asLong(),
                "Bearer " + login.path("result").path("accessToken").asText());
    }

    private JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }

    private record LoggedIn(long accountId, String bearer) {}
}
