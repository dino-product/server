package com.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 탈퇴를 소유한 모듈처럼 루트 계약을 부르고, 로그인은 카카오·Apple 스텁을 거친 실제 API로 확인한다. */
@AutoConfigureMockMvc
@DisplayName("계정 폐쇄")
class AccountClosureIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();
    private static final AppleAuthStub APPLE = AppleAuthStub.start();

    @Autowired
    private AccountClosure accountClosure;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void providerStubs(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
        registry.add("app.auth.apple.token-api.token-uri", APPLE::tokenUri);
        registry.add("app.auth.apple.token-api.revoke-uri", APPLE::revokeUri);
        registry.add("app.auth.apple.client-secret.private-key", APPLE::clientSecretPrivateKeyPem);
    }

    @AfterAll
    static void stopStubs() {
        KAKAO.close();
        APPLE.close();
    }

    @Test
    @DisplayName("닫은 뒤 같은 카카오 계정으로 로그인하면 새 계정을 만들고, 닫은 계정의 회원번호는 연결 해제 대기로 남긴다")
    void kakaoLoginAfterClosureCreatesNewAccount() throws Exception {
        String subject = UUID.randomUUID().toString();
        long closed = accountId(loginWithKakao(subject));

        accountClosure.close(closed);

        JsonNode relogin = loginWithKakao(subject);
        assertThat(accountId(relogin)).isNotEqualTo(closed);
        assertThat(relogin.path("result").path("registered").asBoolean()).isTrue();
        assertThat(pendingUnlinks(closed)).containsExactly(Map.of("provider", "KAKAO", "oauth_uid", subject));
    }

    @Test
    @DisplayName("닫은 뒤 같은 Apple 계정으로 로그인하면 새 계정을 만든다")
    void appleLoginAfterClosureCreatesNewAccount() throws Exception {
        String subject = "001234." + UUID.randomUUID().toString().replace("-", "") + ".0123";
        long closed = accountId(loginWithApple(subject));

        accountClosure.close(closed);

        assertThat(accountId(loginWithApple(subject))).isNotEqualTo(closed);
        assertThat(pendingUnlinks(closed)).containsExactly(Map.of("provider", "APPLE", "oauth_uid", subject));
    }

    @Test
    @DisplayName("이미 닫힌 계정을 다시 닫아도 성공하고 연결 해제 대기를 더 만들지 않는다")
    void closingTwiceIsIdempotent() throws Exception {
        long closed = accountId(loginWithKakao(UUID.randomUUID().toString()));

        accountClosure.close(closed);
        accountClosure.close(closed);

        assertThat(pendingUnlinks(closed)).hasSize(1);
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백되면 외부 식별을 그대로 두어 같은 계정으로 로그인한다")
    void rollbackKeepsIdentity() throws Exception {
        String subject = UUID.randomUUID().toString();
        long accountId = accountId(loginWithKakao(subject));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountClosure.close(accountId);
            status.setRollbackOnly();
        });

        assertThat(accountId(loginWithKakao(subject))).isEqualTo(accountId);
        assertThat(pendingUnlinks(accountId)).isEmpty();
    }

    private JsonNode loginWithKakao(String subject) throws Exception {
        String nonce = issueNonce("/api/v1/auth/kakao/nonces");
        return perform("/api/v1/auth/kakao/login", Map.of("idToken", KAKAO.idToken(subject, nonce)));
    }

    private JsonNode loginWithApple(String subject) throws Exception {
        String nonce =
                HashedNonce.fromRaw(issueNonce("/api/v1/auth/apple/nonces")).value();
        return perform(
                "/api/v1/auth/apple/login",
                Map.of(
                        "idToken", APPLE.idToken(subject, nonce),
                        "authorizationCode", APPLE.issueAuthorizationCode(subject)));
    }

    private String issueNonce(String path) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(post(path))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("nonce")
                .asString();
    }

    private JsonNode perform(String path, Map<String, String> body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<Map<String, Object>> pendingUnlinks(long accountId) {
        return jdbcTemplate.queryForList(
                "select provider, oauth_uid from provider_unlinks where account_id = ?", accountId);
    }

    private static long accountId(JsonNode login) {
        return login.path("result").path("accountId").asLong();
    }
}
