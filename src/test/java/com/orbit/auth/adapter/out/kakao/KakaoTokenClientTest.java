package com.orbit.auth.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.SignedJWT;
import com.orbit.auth.application.port.out.KakaoTokenApiException;
import com.orbit.auth.application.port.out.KakaoTokenApiException.Failure;
import com.orbit.support.KakaoJwksStub;

@DisplayName("카카오 토큰 API 클라이언트")
class KakaoTokenClientTest {

    private static final String REDIRECT_URI = "https://api.example.com/api/v1/auth/kakao/callback";
    private static final String SUBJECT = "4012345678";

    private static KakaoJwksStub kakao;
    private static KakaoTokenClient client;

    @BeforeAll
    static void startStub() {
        kakao = KakaoJwksStub.start();
        client = new KakaoTokenClient(properties(kakao.tokenUri(), KakaoJwksStub.CLIENT_SECRET));
    }

    @AfterAll
    static void stopStub() {
        kakao.close();
    }

    @Test
    @DisplayName("REST API 키·클라이언트 시크릿·콜백 주소로 인가 코드를 교환하고 응답의 id_token을 돌려준다")
    void exchangesCodeForIdToken() throws Exception {
        String code = kakao.issueAuthorizationCode(SUBJECT, "nonce-hash");

        String idToken = client.exchange(code);

        assertThat(SignedJWT.parse(idToken).getJWTClaimsSet().getSubject()).isEqualTo(SUBJECT);
        assertThat(kakao.tokenRequests().getLast())
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("client_id", KakaoJwksStub.REST_API_KEY)
                .containsEntry("client_secret", KakaoJwksStub.CLIENT_SECRET)
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("code", code);
    }

    @Test
    @DisplayName("카카오가 만료·재사용된 인가 코드를 거절하면(KOE320) REJECTED다")
    void rejectsUsedCode() {
        String code = kakao.issueAuthorizationCode(SUBJECT, "nonce-hash");
        client.exchange(code);

        assertThatThrownBy(() -> client.exchange(code))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.REJECTED))
                .hasMessageNotContaining(code);
    }

    @Test
    @DisplayName("invalid_grant라도 콜백 주소 불일치(KOE303)·grant_type 오류(KOE310)는 서버 설정 문제라 UNAVAILABLE이다")
    void treatsConfigurationGrantErrorsAsUnavailable() {
        kakao.failNextTokenRequests(
                1, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"x\",\"error_code\":\"KOE303\"}");
        assertThatThrownBy(() -> client.exchange("kakao-code"))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));

        kakao.failNextTokenRequests(
                1, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"x\",\"error_code\":\"KOE310\"}");
        assertThatThrownBy(() -> client.exchange("kakao-code"))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));
    }

    @Test
    @DisplayName("클라이언트 시크릿이 틀리거나(KOE010) 카카오가 장애 응답을 주면 UNAVAILABLE이다")
    void treatsClientAndServerErrorsAsUnavailable() {
        KakaoTokenClient wrongSecret = new KakaoTokenClient(properties(kakao.tokenUri(), "wrong-secret"));
        String code = kakao.issueAuthorizationCode(SUBJECT, "nonce-hash");
        assertThatThrownBy(() -> wrongSecret.exchange(code))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));

        kakao.failNextTokenRequests(1, 503, "service unavailable");
        assertThatThrownBy(() -> client.exchange(kakao.issueAuthorizationCode(SUBJECT, "nonce-hash")))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));
    }

    @Test
    @DisplayName("응답에 id_token이 없으면(OpenID Connect 비활성화) UNAVAILABLE이다")
    void requiresIdTokenInResponse() {
        kakao.failNextTokenRequests(
                1, 200, "{\"token_type\":\"bearer\",\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":1}");

        assertThatThrownBy(() -> client.exchange("kakao-code"))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));
    }

    @Test
    @DisplayName("카카오에 연결하지 못하면 UNAVAILABLE이다")
    void treatsConnectionFailureAsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        KakaoTokenClient unreachable = new KakaoTokenClient(
                properties("http://127.0.0.1:" + closedPort + "/oauth/token", KakaoJwksStub.CLIENT_SECRET));

        assertThatThrownBy(() -> unreachable.exchange("kakao-code"))
                .isInstanceOfSatisfying(KakaoTokenApiException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));
    }

    private static KakaoWebLoginProperties properties(String tokenUri, String clientSecret) {
        return new KakaoWebLoginProperties(
                "https://kauth.kakao.com/oauth/authorize",
                tokenUri,
                KakaoJwksStub.REST_API_KEY,
                clientSecret,
                REDIRECT_URI,
                Duration.ofSeconds(2));
    }
}
