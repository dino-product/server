package com.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.auth.application.port.in.command.RetryProviderUnlinksUseCase;
import com.orbit.auth.domain.HashedNonce;
import com.orbit.support.AppleAuthStub;
import com.orbit.support.IntegrationTestSupport;
import com.orbit.support.KakaoAdminApiStub;
import com.orbit.support.KakaoJwksStub;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 계정 폐쇄 뒤 카카오 연결 끊기·Apple 토큰 철회를 로컬 스텁 서버로 확인한다. 로그인·폐쇄·커밋 뒤 비동기 처리·재시도는 실제 경로를 지난다. 제공자 E2E는 아니다. 주기
 * 작업은 테스트 프로필에서 꺼 두고 재시도는 유즈케이스를 직접 부른다.
 */
@DisplayName("외부 연결 해제")
class ProviderUnlinkIntegrationTest extends IntegrationTestSupport {

    private static final KakaoJwksStub KAKAO = KakaoJwksStub.start();
    private static final KakaoAdminApiStub KAKAO_ADMIN = KakaoAdminApiStub.start();
    private static final AppleAuthStub APPLE = AppleAuthStub.start();
    private static final Duration ASYNC_WAIT = Duration.ofSeconds(10);

    @Autowired
    private AccountClosure accountClosure;

    @Autowired
    private RetryProviderUnlinksUseCase retryProviderUnlinks;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final LogCollector logs = new LogCollector();
    private final List<Logger> authLoggers = List.of((Logger) LoggerFactory.getLogger("com.orbit.auth"));

    @DynamicPropertySource
    static void providerStubs(DynamicPropertyRegistry registry) {
        registry.add("app.auth.kakao.jwk-set-uri", KAKAO::jwkSetUri);
        registry.add("app.auth.kakao.admin-api.unlink-uri", KAKAO_ADMIN::unlinkUri);
        registry.add("app.auth.kakao.admin-api.admin-key", () -> KakaoAdminApiStub.ADMIN_KEY);
        registry.add("app.auth.apple.jwk-set-uri", APPLE::jwkSetUri);
        registry.add("app.auth.apple.token-api.token-uri", APPLE::tokenUri);
        registry.add("app.auth.apple.token-api.revoke-uri", APPLE::revokeUri);
        registry.add("app.auth.apple.client-secret.private-key", APPLE::clientSecretPrivateKeyPem);
    }

    @AfterAll
    static void stopStubs() {
        KAKAO.close();
        KAKAO_ADMIN.close();
        APPLE.close();
    }

    @BeforeEach
    void captureLogs() {
        logs.start();
        authLoggers.forEach(logger -> logger.addAppender(logs));
    }

    @AfterEach
    void stopCapturingLogs() {
        authLoggers.forEach(logger -> logger.detachAppender(logs));
        logs.stop();
    }

    @Test
    @DisplayName("폐쇄가 커밋되면 어드민 키로 카카오 연결을 끊고 대기를 지운다")
    void unlinksKakaoAfterCommit() throws Exception {
        String subject = kakaoSubject();
        long accountId = accountId(loginWithKakao(subject));

        accountClosure.close(accountId);

        await().atMost(ASYNC_WAIT).until(() -> pendingUnlinks(accountId).isEmpty());
        assertThat(kakaoUnlinkRequests(subject)).singleElement().satisfies(request -> {
            assertThat(request.authorization()).isEqualTo("KakaoAK " + KakaoAdminApiStub.ADMIN_KEY);
            assertThat(request.form()).containsEntry("target_id_type", "user_id");
        });
    }

    @Test
    @DisplayName("폐쇄가 커밋되면 보관한 Apple refresh token을 발급 클라이언트로 철회하고 토큰과 대기를 지운다")
    void revokesAppleAfterCommit() throws Exception {
        long accountId = accountId(loginWithApple(appleSubject()));
        assertThat(storedAppleRefreshTokenCount(accountId)).isEqualTo(1);
        String refreshToken = APPLE.issuedRefreshTokens().getLast();

        accountClosure.close(accountId);

        await().atMost(ASYNC_WAIT).until(() -> pendingUnlinks(accountId).isEmpty());
        assertThat(storedAppleRefreshTokenCount(accountId)).isZero();
        assertThat(APPLE.revokeRequests()).anySatisfy(request -> assertThat(request)
                .containsEntry("client_id", AppleAuthStub.BUNDLE_ID)
                .containsEntry("token", refreshToken)
                .containsEntry("token_type_hint", "refresh_token"));
    }

    @Test
    @DisplayName("폐쇄가 롤백되면 외부 제공자를 부르지 않는다")
    void doesNotCallProvidersOnRollback() throws Exception {
        String subject = kakaoSubject();
        long accountId = accountId(loginWithKakao(subject));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountClosure.close(accountId);
            status.setRollbackOnly();
        });

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(() -> kakaoUnlinkRequests(subject)
                .isEmpty());
    }

    @Test
    @DisplayName("카카오가 일시적으로 실패하면 대기를 남겨 재시도로 끊고, 로그에 회원번호를 남기지 않는다")
    void retriesAfterTemporaryFailure() throws Exception {
        String subject = kakaoSubject();
        long accountId = accountId(loginWithKakao(subject));
        KAKAO_ADMIN.failNext(503, -9798);

        accountClosure.close(accountId);

        await().atMost(ASYNC_WAIT).until(() -> failedAttempts(accountId) == 1);
        jdbcTemplate.update(
                "update provider_unlinks set next_attempt_at = now() - interval '1 second' where account_id = ?",
                accountId);
        retryProviderUnlinks.retryDue();

        assertThat(pendingUnlinks(accountId)).isEmpty();
        assertThat(kakaoUnlinkRequests(subject)).hasSize(2);
        assertThat(logs.messages()).isNotEmpty().noneMatch(message -> message.contains(subject));
    }

    @Test
    @DisplayName("카카오가 거절하면 다시 시도하지 않고 대기를 지운다")
    void dropsRejectedUnlink() throws Exception {
        String subject = kakaoSubject();
        long accountId = accountId(loginWithKakao(subject));
        KAKAO_ADMIN.failNext(400, -103);

        accountClosure.close(accountId);

        await().atMost(ASYNC_WAIT).until(() -> pendingUnlinks(accountId).isEmpty());
        assertThat(kakaoUnlinkRequests(subject)).hasSize(1);
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

    private List<KakaoAdminApiStub.Request> kakaoUnlinkRequests(String subject) {
        return KAKAO_ADMIN.requests().stream()
                .filter(request -> subject.equals(request.form().get("target_id")))
                .toList();
    }

    private List<Map<String, Object>> pendingUnlinks(long accountId) {
        return jdbcTemplate.queryForList("select provider from provider_unlinks where account_id = ?", accountId);
    }

    private int failedAttempts(long accountId) {
        return jdbcTemplate
                .queryForList(
                        "select failed_attempts from provider_unlinks where account_id = ?", Integer.class, accountId)
                .stream()
                .findFirst()
                .orElse(-1);
    }

    private int storedAppleRefreshTokenCount(long accountId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from apple_refresh_tokens where account_id = ?", Integer.class, accountId);
    }

    private static String kakaoSubject() {
        return Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000L));
    }

    private static String appleSubject() {
        return "001234." + UUID.randomUUID().toString().replace("-", "") + ".0123";
    }

    private static long accountId(JsonNode login) {
        return login.path("result").path("accountId").asLong();
    }

    private static final class LogCollector extends AppenderBase<ILoggingEvent> {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            messages.add(event.getFormattedMessage());
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
