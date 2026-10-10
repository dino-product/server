package com.orbit.profile.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import com.orbit.support.SignupTestSupport;
import com.orbit.support.SignupTestSupport.TestAccount;

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
        TestAccount account = loginAsNewAccount();

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
        TestAccount account = loginAsNewAccount();

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
        TestAccount account = loginAsNewAccount();

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
        TestAccount account = loginAsNewAccount();
        TestAccount other = loginAsNewAccount();

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
        TestAccount account = loginAsNewAccount();
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
        TestAccount account = loginAsNewAccount();

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
    @DisplayName("가입 미완료 계정은 프로필·인증 API만 쓰고 그 밖의 API는 PROFILE-005로 막히며, 가입을 마치면 풀린다")
    void blocksOtherApisUntilSignupCompleted() throws Exception {
        TestAccount account = loginAsNewAccount();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isOk());
        registerExampleUser(account)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PROFILE-005"));

        saveProfile(account, account.accountId(), "홍길동", "01012345678").andExpect(status().isOk());
        registerExampleUser(account).andExpect(jsonPath("$.code").value("PROFILE-005"));

        agreeToTerms(account, account.accountId(), true, true, false).andExpect(status().isOk());
        registerExampleUser(account).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 모듈 테스트가 쓰는 가입 완료 지원으로 만든 계정은 공통 검사를 통과한다")
    void signupSupportProducesUsableAccount() throws Exception {
        TestAccount account = SignupTestSupport.signUpNewAccount(mockMvc, objectMapper, KAKAO);

        registerExampleUser(account).andExpect(status().isOk());
    }

    @Test
    @DisplayName("가입 미완료 계정이 없는 경로를 부르면 검사하지 않고 그대로 404다")
    void keepsNotFoundForMissingPathBeforeSignup() throws Exception {
        TestAccount account = loginAsNewAccount();

        mockMvc.perform(get("/api/v1/no-such-resource").header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"));
    }

    @Test
    @DisplayName("가입을 마친 계정은 마이페이지에서 마케팅 수신 동의를 켜고 끌 수 있고, 가입 전에는 PROFILE-005로 거부한다")
    void changesMarketingConsentAfterSignup() throws Exception {
        TestAccount pending = loginAsNewAccount();
        changeMarketingConsent(pending, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE-005"));

        TestAccount account = SignupTestSupport.signUpNewAccount(mockMvc, objectMapper, KAKAO);
        changeMarketingConsent(account, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marketingAgreed").value(true));
        changeMarketingConsent(account, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marketingAgreed").value(false));
        getProfile(account).andExpect(jsonPath("$.result.marketingAgreed").value(false));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/{accountId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private ResultActions getProfile(TestAccount account) throws Exception {
        return mockMvc.perform(get("/api/v1/profiles/{accountId}", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private ResultActions saveProfile(TestAccount account, long accountId, String name, String phoneNumber)
            throws Exception {
        return mockMvc.perform(put("/api/v1/profiles/{accountId}", accountId)
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SaveProfileRequest(name, phoneNumber))));
    }

    private ResultActions agreeToTerms(
            TestAccount account, long accountId, Boolean serviceTerms, Boolean privacyPolicy, Boolean marketing)
            throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/{accountId}/terms-agreements", accountId)
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AgreeToTermsRequest(serviceTerms, privacyPolicy, marketing))));
    }

    /** 공통 검사 대상인 허용 목록 밖 API로 예제 사용자 등록을 쓴다. 토큰 없이도 열린 경로라 검사 결과만 드러난다. */
    private ResultActions registerExampleUser(TestAccount account) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("displayName", "예제 사용자"))));
    }

    private ResultActions changeMarketingConsent(TestAccount account, Boolean agreed) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/{accountId}/marketing-consents", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeMarketingConsentRequest(agreed))));
    }

    private TestAccount loginAsNewAccount() throws Exception {
        return SignupTestSupport.loginAsNewAccount(mockMvc, objectMapper, KAKAO);
    }
}
