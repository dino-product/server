package com.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoJwksStub;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 탈퇴를 소유한 모듈처럼 루트 계약을 부르고, 로그인은 카카오·Apple 스텁을 거친 실제 API로 확인한다. */
@DisplayName("계정 폐쇄")
class AccountClosureIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();
    private static final AppleAuthStub APPLE = AppleAuthStub.start();
    private static final String CLOSED_ACCOUNT_KEY = "auth:closed-account:";

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${app.auth.jwt.access-token-ttl}")
    private Duration accessTokenTtl;

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
    @DisplayName("같은 계정을 동시에 닫으면 뒤에 온 호출은 앞선 폐쇄가 끝날 때까지 기다린 뒤 아무것도 바꾸지 않고 성공한다")
    void serializesConcurrentClosures() throws Exception {
        long accountId = accountId(loginWithKakao(UUID.randomUUID().toString()));
        CountDownLatch firstClosed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        accountClosure.close(accountId);
                        firstClosed.countDown();
                        awaitRelease(releaseFirst);
                    }));
            assertThat(firstClosed.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> accountClosure.close(accountId));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .as("앞선 폐쇄가 커밋될 때까지 기다린다")
                    .isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertThat(pendingUnlinks(accountId)).hasSize(1);
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백되면 외부 식별과 토큰을 그대로 두어 같은 계정으로 로그인하고 인증된다")
    void rollbackKeepsIdentity() throws Exception {
        String subject = UUID.randomUUID().toString();
        JsonNode login = loginWithKakao(subject);
        long accountId = accountId(login);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountClosure.close(accountId);
            status.setRollbackOnly();
        });

        assertThat(closedMark(accountId)).isFalse();
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk());
        refresh(refreshToken(login)).andExpect(status().isOk());
        assertThat(accountId(loginWithKakao(subject))).isEqualTo(accountId);
        assertThat(pendingUnlinks(accountId)).isEmpty();
    }

    @Test
    @DisplayName("폐쇄가 커밋되면 그 전에 발급한 Access Token을 폐기 토큰과 같은 404로 거부한다")
    void rejectsTokensIssuedBeforeClosure() throws Exception {
        JsonNode login = loginWithKakao(UUID.randomUUID().toString());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk());

        accountClosure.close(accountId(login));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"));
    }

    @Test
    @DisplayName("폐쇄하면 수명이 다른 세션까지 그 계정의 모든 기기 세션을 지워 refresh token을 거절한다")
    void revokesAllRefreshSessionsOnClosure() throws Exception {
        String subject = UUID.randomUUID().toString();
        JsonNode phone = loginWithKakao(subject);
        JsonNode tablet = loginWithKakao(subject);
        // 한 세션만 회전시켜 만료 시각이 다른 세션을 함께 둔다.
        String rotatedTablet = objectMapper
                .readTree(refresh(refreshToken(tablet))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("refreshToken")
                .asString();

        accountClosure.close(accountId(phone));

        assertThat(refreshSessions(accountId(phone))).isZero();
        refresh(refreshToken(phone))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-009"));
        refresh(rotatedTablet)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-009"));
    }

    @Test
    @DisplayName("계정 폐쇄 표시는 커밋 전에는 남기지 않고 커밋 뒤에 Access Token 수명만큼 남긴다")
    void marksClosedAccountOnlyAfterCommit() throws Exception {
        long accountId = accountId(loginWithKakao(UUID.randomUUID().toString()));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountClosure.close(accountId);
            assertThat(closedMark(accountId)).as("커밋 전").isFalse();
        });

        assertThat(closedMark(accountId)).as("커밋 뒤").isTrue();
        assertThat(redisTemplate.getExpire(CLOSED_ACCOUNT_KEY + accountId, TimeUnit.SECONDS))
                .as("폐쇄 직전에 시작한 로그인이 발급한 토큰까지 덮도록 수명보다 조금 길게 남긴다")
                .isBetween(
                        accessTokenTtl.toSeconds() + 1,
                        accessTokenTtl.plusMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("폐쇄 표시가 사라졌으면 같은 계정을 다시 닫을 때 표시를 되살려 이전 토큰을 거부한다")
    void restoresClosedMarkWhenClosedAgain() throws Exception {
        JsonNode login = loginWithKakao(UUID.randomUUID().toString());
        long accountId = accountId(login);
        accountClosure.close(accountId);
        redisTemplate.delete(CLOSED_ACCOUNT_KEY + accountId);

        accountClosure.close(accountId);

        assertThat(closedMark(accountId)).isTrue();
        assertThat(pendingUnlinks(accountId)).hasSize(1);
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"));
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

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private static String refreshToken(JsonNode login) {
        return login.path("result").path("refreshToken").asString();
    }

    private Integer refreshSessions(long accountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where account_id = ?", Integer.class, accountId);
    }

    private List<Map<String, Object>> pendingUnlinks(long accountId) {
        return jdbcTemplate.queryForList(
                "select provider, oauth_uid from provider_unlinks where account_id = ?", accountId);
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first closure was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private boolean closedMark(long accountId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CLOSED_ACCOUNT_KEY + accountId));
    }

    private static String bearer(JsonNode login) {
        return "Bearer " + login.path("result").path("accessToken").asString();
    }

    private static long accountId(JsonNode login) {
        return login.path("result").path("accountId").asLong();
    }
}
