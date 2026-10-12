package com.orbit.profile.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.auth.AccountClosure;
import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;
import com.orbit.support.SignupTestSupport;
import com.orbit.support.SignupTestSupport.TestAccount;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제 Access Token으로 탈퇴하고, 같은 토큰·같은 카카오 계정의 다음 동작을 auth까지 이어서 확인한다. 카카오 id_token은 로컬 JWKS 스텁이 서명한다. */
@AutoConfigureMockMvc
@Import(ProfileWithdrawalApiIntegrationTest.TestWithdrawalBlockers.class)
@DisplayName("회원 탈퇴 API")
class ProfileWithdrawalApiIntegrationTest extends IntegrationTestSupport {

    /** 탈퇴를 막을 계정. 실제 차단 판정(organization·schedule)은 다음 작업이라 테스트 구현으로 대신한다. */
    private static final Set<Long> BLOCKED_ACCOUNTS = ConcurrentHashMap.newKeySet();

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AccountClosure accountClosure;

    @DynamicPropertySource
    static void kakaoJwks(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
    }

    @AfterAll
    static void stopKakaoJwks() {
        KAKAO.close();
    }

    @Test
    @DisplayName("가입을 마친 계정이 탈퇴하면 이름·연락처를 지우고 탈퇴 상태가 되며, 같은 토큰은 폐기된 토큰과 같은 404를 받는다")
    void withdrawsActiveAccount() throws Exception {
        String subject = kakaoSubject();
        TestAccount account = login(subject);
        SignupTestSupport.completeSignup(mockMvc, objectMapper, account);

        withdraw(account, account.accountId()).andExpect(status().isNoContent());

        assertThat(storedProfile(account.accountId()))
                .containsEntry("status", "WITHDRAWN")
                .containsEntry("name", null)
                .containsEntry("phone_number", null);
        assertThat(storedProfile(account.accountId()).get("withdrawn_at")).isNotNull();
        assertThat(agreementCount(account.accountId())).isEqualTo(2);
        getProfile(account)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"));
    }

    @Test
    @DisplayName("프로필을 입력하기 전인 가입 미완료 계정도 탈퇴할 수 있다")
    void withdrawsAccountBeforeProfile() throws Exception {
        TestAccount account = login(kakaoSubject());

        withdraw(account, account.accountId()).andExpect(status().isNoContent());

        assertThat(storedProfile(account.accountId())).containsEntry("status", "WITHDRAWN");
        getProfile(account).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("탈퇴한 뒤 같은 카카오 계정으로 로그인하면 새 계정이고 가입 미완료로 시작한다")
    void loginAfterWithdrawalStartsNewSignup() throws Exception {
        String subject = kakaoSubject();
        TestAccount withdrawn = login(subject);
        SignupTestSupport.completeSignup(mockMvc, objectMapper, withdrawn);
        withdraw(withdrawn, withdrawn.accountId()).andExpect(status().isNoContent());

        TestAccount renewed = login(subject);

        assertThat(renewed.accountId()).isNotEqualTo(withdrawn.accountId());
        getProfile(renewed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING_SIGNUP"))
                .andExpect(jsonPath("$.result.nextStep").value("PROFILE"));
    }

    @Test
    @DisplayName("다른 계정은 탈퇴시킬 수 없고 존재를 드러내지 않는 PROFILE-002를 받는다")
    void rejectsOtherAccount() throws Exception {
        TestAccount requester = login(kakaoSubject());
        TestAccount other = login(kakaoSubject());
        SignupTestSupport.completeSignup(mockMvc, objectMapper, other);

        withdraw(requester, other.accountId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE-002"));
        assertThat(storedProfile(other.accountId())).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("auth 계정 폐쇄가 실패하면 탈퇴도 롤백해 프로필과 토큰을 그대로 둔다")
    void rollsBackWhenAccountClosureFails() throws Exception {
        TestAccount account = login(kakaoSubject());
        SignupTestSupport.completeSignup(mockMvc, objectMapper, account);
        doThrow(new IllegalStateException("closure failed"))
                .when(accountClosure)
                .close(anyLong());

        withdraw(account, account.accountId()).andExpect(status().isInternalServerError());

        assertThat(storedProfile(account.accountId()))
                .containsEntry("status", "ACTIVE")
                .containsEntry("name", "테스트");
        getProfile(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("막는 조건이 없으면 탈퇴 가능 여부 확인은 탈퇴할 수 있다고 알린다")
    void reportsWithdrawableAccount() throws Exception {
        TestAccount account = login(kakaoSubject());

        checkEligibility(account, account.accountId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.withdrawable").value(true))
                .andExpect(jsonPath("$.result.blocks").isEmpty());
    }

    @Test
    @DisplayName("차단 판정 구현이 막으면 확인 API는 사유와 건수를 알리고, 탈퇴 요청은 PROFILE-006으로 거부해 아무것도 바꾸지 않는다")
    void rejectsBlockedWithdrawal() throws Exception {
        TestAccount account = login(kakaoSubject());
        SignupTestSupport.completeSignup(mockMvc, objectMapper, account);
        BLOCKED_ACCOUNTS.add(account.accountId());

        checkEligibility(account, account.accountId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.withdrawable").value(false))
                .andExpect(jsonPath("$.result.blocks[0].reason").value("TEST_BLOCK"))
                .andExpect(jsonPath("$.result.blocks[0].count").value(2));
        withdraw(account, account.accountId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE-006"));

        assertThat(storedProfile(account.accountId())).containsEntry("status", "ACTIVE");
        getProfile(account).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 계정의 탈퇴 가능 여부는 PROFILE-002로 거부한다")
    void hidesOtherAccountEligibility() throws Exception {
        TestAccount requester = login(kakaoSubject());
        TestAccount other = login(kakaoSubject());

        checkEligibility(requester, other.accountId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE-002"));
    }

    private ResultActions checkEligibility(TestAccount requester, long accountId) throws Exception {
        return mockMvc.perform(get("/api/v1/profiles/{accountId}/withdrawal-eligibility", accountId)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer()));
    }

    private TestAccount login(String subject) throws Exception {
        JsonNode nonce = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/kakao/nonces"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String idToken =
                KAKAO.idToken(subject, nonce.path("result").path("nonce").asString());
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("idToken", idToken))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return new TestAccount(
                login.path("result").path("accountId").asLong(),
                "Bearer " + login.path("result").path("accessToken").asString());
    }

    private ResultActions withdraw(TestAccount requester, long accountId) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/{accountId}/withdrawals", accountId)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer()));
    }

    private ResultActions getProfile(TestAccount account) throws Exception {
        return mockMvc.perform(get("/api/v1/profiles/{accountId}", account.accountId())
                .header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private Map<String, Object> storedProfile(long accountId) {
        return jdbcTemplate.queryForMap(
                "select status, name, phone_number, withdrawn_at from profiles where account_id = ?", accountId);
    }

    private int agreementCount(long accountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from profile_terms_agreements where account_id = ?", Integer.class, accountId);
    }

    private static String kakaoSubject() {
        return UUID.randomUUID().toString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestWithdrawalBlockers {

        @Bean
        WithdrawalBlocker testWithdrawalBlocker() {
            return accountId ->
                    BLOCKED_ACCOUNTS.contains(accountId) ? List.of(new WithdrawalBlock("TEST_BLOCK", 2)) : List.of();
        }
    }
}
