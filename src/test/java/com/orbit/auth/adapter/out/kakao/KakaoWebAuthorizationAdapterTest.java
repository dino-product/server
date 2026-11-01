package com.orbit.auth.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.HashedNonce;

@DisplayName("카카오 웹 로그인 인가 주소")
class KakaoWebAuthorizationAdapterTest {

    private static final String REST_API_KEY = "rest-api-key";
    private static final String REDIRECT_URI = "https://api.example.com/api/v1/auth/kakao/callback";

    @Test
    @DisplayName("REST API 키·콜백 주소·openid 범위·state·nonce 해시를 실어 카카오 인가 주소를 만든다")
    void buildsAuthorizationUri() {
        KakaoWebAuthorizationAdapter adapter =
                new KakaoWebAuthorizationAdapter(properties("secret"), oidc(List.of("native-key", REST_API_KEY)));
        HashedNonce nonce = HashedNonce.fromRaw("raw-nonce");

        URI uri = adapter.authorizationUri("state-value", nonce);

        assertThat(uri)
                .hasScheme("https")
                .hasHost("kauth.kakao.com")
                .hasPath("/oauth/authorize")
                .hasParameter("response_type", "code")
                .hasParameter("client_id", REST_API_KEY)
                .hasParameter("redirect_uri", REDIRECT_URI)
                .hasParameter("scope", "openid")
                .hasParameter("state", "state-value")
                .hasParameter("nonce", nonce.value());
        assertThat(uri.toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("REST API 키가 id_token 허용 앱 키 목록에 없으면 기동하지 않는다")
    void requiresRestApiKeyInAllowedAudiences() {
        assertThatThrownBy(() -> new KakaoWebAuthorizationAdapter(properties("secret"), oidc(List.of("native-key"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-audiences");
    }

    @Test
    @DisplayName("설정의 문자열 표현에 클라이언트 시크릿을 남기지 않고, 필수 값이 비면 기동하지 않는다")
    void masksClientSecretAndRequiresValues() {
        assertThat(properties("super-secret").toString()).doesNotContain("super-secret");
        assertThatThrownBy(() -> properties(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KakaoWebLoginProperties(
                        "https://kauth.kakao.com/oauth/authorize",
                        "https://kauth.kakao.com/oauth/token",
                        REST_API_KEY,
                        "secret",
                        REDIRECT_URI,
                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static KakaoWebLoginProperties properties(String clientSecret) {
        return new KakaoWebLoginProperties(
                "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token",
                REST_API_KEY,
                clientSecret,
                REDIRECT_URI,
                Duration.ofSeconds(5));
    }

    private static KakaoOidcProperties oidc(List<String> audiences) {
        return new KakaoOidcProperties(
                "https://kauth.kakao.com", "https://kauth.kakao.com/.well-known/jwks.json", audiences);
    }
}
