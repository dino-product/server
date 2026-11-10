package com.orbit.profile.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;
import com.orbit.support.SignupTestSupport;
import com.orbit.support.SignupTestSupport.TestAccount;

import tools.jackson.databind.ObjectMapper;

@DisplayName("약관 API")
class TermsApiIntegrationTest extends IntegrationTestSupport {

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
    @DisplayName("로그인하지 않아도 약관 문서마다 시행 중인 버전과 본문 주소를 받는다")
    void listsCurrentTermsWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.terms.length()").value(4))
                .andExpect(jsonPath("$.result.terms[0].type").value("SERVICE"))
                .andExpect(jsonPath("$.result.terms[0].version").value("2026-10-10"))
                .andExpect(
                        jsonPath("$.result.terms[0].url").value("https://dev.orbi-t.com/terms/service/2026-10-10.txt"))
                .andExpect(jsonPath("$.result.terms[1].type").value("PRIVACY"))
                .andExpect(jsonPath("$.result.terms[1].url")
                        .value("https://dev.orbi-t.com/terms/privacy-consent/2026-10-10.txt"))
                .andExpect(jsonPath("$.result.terms[2].type").value("MARKETING"))
                .andExpect(jsonPath("$.result.terms[3].type").value("PRIVACY_POLICY"))
                .andExpect(jsonPath("$.result.terms[3].url")
                        .value("https://dev.orbi-t.com/terms/privacy-policy/2026-10-10.txt"));
    }

    @Test
    @DisplayName("가입을 마치지 않은 계정도 약관 동의 화면에서 약관을 받는다")
    void listsCurrentTermsBeforeSignupCompletes() throws Exception {
        TestAccount account = SignupTestSupport.loginAsNewAccount(mockMvc, objectMapper, KAKAO);

        mockMvc.perform(get("/api/v1/terms").header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.terms.length()").value(4));
    }

    @Test
    @DisplayName("공개 API라도 무효한 토큰을 붙이면 없는 자원과 같은 404다")
    void rejectsInvalidTokenLikeOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/terms").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"));
    }
}
