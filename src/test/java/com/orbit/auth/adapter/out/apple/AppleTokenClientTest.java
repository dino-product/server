package com.orbit.auth.adapter.out.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.auth.adapter.out.oidc.OidcIdTokenDecoder;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.AppleTokenApiException.Failure;
import com.orbit.support.AppleAuthStub;

@DisplayName("Apple 토큰 API 클라이언트")
class AppleTokenClientTest {

    private static final String SUBJECT = "001234.0123456789abcdef0123456789abcdef.0123";
    private static final AppleOidcProperties OIDC = new AppleOidcProperties(
            AppleAuthStub.ISSUER, "unused", List.of(AppleAuthStub.BUNDLE_ID, AppleAuthStub.SERVICES_ID));

    private static AppleAuthStub apple;
    private static AppleTokenClient client;

    @BeforeAll
    static void startStub() throws Exception {
        apple = AppleAuthStub.start();
        client = client(apple.tokenUri(), apple.clientSecretPrivateKeyPem());
    }

    @AfterAll
    static void stopStub() {
        apple.close();
    }

    @Test
    @DisplayName("ES256 client_secret으로 code를 교환하고 응답 id_token의 sub와 refresh token을 돌려준다")
    void exchangesCodeWithClientSecret() throws Exception {
        String code = apple.issueAuthorizationCode(SUBJECT);

        AppleCodeExchange exchange = client.exchange(AppleAuthStub.BUNDLE_ID, code, null);

        assertThat(exchange.subject()).isEqualTo(SUBJECT);
        assertThat(exchange.clientId()).isEqualTo(AppleAuthStub.BUNDLE_ID);
        assertThat(exchange.refreshToken()).startsWith("apple-refresh-");
        Map<String, String> request = apple.tokenRequests().getLast();
        assertThat(request)
                .containsEntry("client_id", AppleAuthStub.BUNDLE_ID)
                .containsEntry("code", code)
                .containsEntry("grant_type", "authorization_code")
                .doesNotContainKey("redirect_uri");
        assertThat(SignedJWT.parse(request.get("client_secret"))
                        .getJWTClaimsSet()
                        .getSubject())
                .isEqualTo(AppleAuthStub.BUNDLE_ID);
    }

    @Test
    @DisplayName("웹 흐름의 redirect_uri를 함께 보낸다")
    void sendsRedirectUriForWebFlow() {
        client.exchange(
                AppleAuthStub.SERVICES_ID,
                apple.issueAuthorizationCode(SUBJECT),
                "https://api.example.com/api/v1/auth/apple/callback");

        assertThat(apple.tokenRequests().getLast())
                .containsEntry("redirect_uri", "https://api.example.com/api/v1/auth/apple/callback");
    }

    @Test
    @DisplayName("Apple이 code를 거절(invalid_grant)하면 REJECTED다")
    void reportsRejectedCode() {
        String code = apple.issueAuthorizationCode(SUBJECT);
        client.exchange(AppleAuthStub.BUNDLE_ID, code, null);

        assertFailure(() -> client.exchange(AppleAuthStub.BUNDLE_ID, code, null), Failure.REJECTED);
    }

    @Test
    @DisplayName("client_secret을 Apple이 받지 않으면(invalid_client) 서버 설정 문제로 UNAVAILABLE이다")
    void reportsInvalidClientAsUnavailable() throws Exception {
        String otherKey;
        try (AppleAuthStub other = AppleAuthStub.start()) {
            otherKey = other.clientSecretPrivateKeyPem();
        }
        AppleTokenClient misconfigured = client(apple.tokenUri(), otherKey);

        assertFailure(
                () -> misconfigured.exchange(AppleAuthStub.BUNDLE_ID, apple.issueAuthorizationCode(SUBJECT), null),
                Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("Apple 서버 오류 응답은 UNAVAILABLE이다")
    void reportsServerErrorAsUnavailable() {
        apple.failNextTokenRequests(1, 503, "{}");

        assertFailure(
                () -> client.exchange(AppleAuthStub.BUNDLE_ID, apple.issueAuthorizationCode(SUBJECT), null),
                Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("refresh token이 없는 응답은 UNAVAILABLE이다")
    void reportsResponseWithoutRefreshTokenAsUnavailable() {
        apple.failNextTokenRequests(1, 200, "{\"access_token\":\"a\",\"id_token\":\"x\"}");

        assertFailure(
                () -> client.exchange(AppleAuthStub.BUNDLE_ID, apple.issueAuthorizationCode(SUBJECT), null),
                Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("응답 id_token의 서명이 Apple 키가 아니면 UNAVAILABLE이다")
    void reportsForgedResponseIdTokenAsUnavailable() {
        apple.failNextTokenRequests(
                1, 200, "{\"refresh_token\":\"r\",\"id_token\":\"" + apple.forgedIdToken(SUBJECT, null) + "\"}");

        assertFailure(
                () -> client.exchange(AppleAuthStub.BUNDLE_ID, apple.issueAuthorizationCode(SUBJECT), null),
                Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("Apple에 연결하지 못하면 UNAVAILABLE이다")
    void reportsUnreachableEndpointAsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AppleTokenClient unreachable =
                client("http://127.0.0.1:" + closedPort + "/auth/token", apple.clientSecretPrivateKeyPem());

        assertFailure(() -> unreachable.exchange(AppleAuthStub.BUNDLE_ID, "code", null), Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("개인키 설정이 잘못되면 Apple을 호출하지 않고 UNAVAILABLE이다")
    void reportsInvalidPrivateKeyAsUnavailable() {
        int before = apple.tokenRequests().size();
        AppleTokenClient misconfigured = client(apple.tokenUri(), "not-a-key");

        assertFailure(() -> misconfigured.exchange(AppleAuthStub.BUNDLE_ID, "code", null), Failure.UNAVAILABLE);
        assertThat(apple.tokenRequests()).hasSize(before);
    }

    @Test
    @DisplayName("교환 결과의 문자열 표현에 refresh token을 남기지 않는다")
    void masksRefreshTokenInToString() {
        assertThat(new AppleCodeExchange(SUBJECT, AppleAuthStub.BUNDLE_ID, "secret-refresh").toString())
                .doesNotContain("secret-refresh");
    }

    private static void assertFailure(Runnable call, Failure failure) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(AppleTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(failure));
    }

    private static AppleTokenClient client(String tokenUri, String privateKey) {
        try {
            JWKSource<SecurityContext> jwks = OidcIdTokenDecoder.remoteJwkSource(apple.jwkSetUri());
            return new AppleTokenClient(
                    new AppleTokenApiProperties(tokenUri, apple.revokeUri(), Duration.ofSeconds(2)),
                    new AppleClientSecretFactory(
                            new AppleClientSecretProperties(
                                    AppleAuthStub.TEAM_ID, AppleAuthStub.KEY_ID, privateKey, Duration.ofMinutes(10)),
                            Clock.systemUTC()),
                    jwks,
                    OIDC,
                    Clock.systemUTC());
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
