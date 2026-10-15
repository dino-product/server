package com.orbit.auth.adapter.out.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.domain.HashedNonce;

@DisplayName("Apple 웹 인가 주소")
class AppleWebAuthorizationAdapterTest {

    private static final AppleOidcProperties OIDC = new AppleOidcProperties(
            "https://appleid.apple.com",
            "https://appleid.apple.com/auth/keys",
            List.of("com.orbit.app", "com.orbit.web"));
    private static final AppleWebLoginProperties WEB = new AppleWebLoginProperties(
            "https://appleid.apple.com/auth/authorize",
            "com.orbit.web",
            "https://api.example.com/api/v1/auth/apple/callback");

    @Test
    @DisplayName("code·id_token을 form_post로 받도록 Services ID·콜백·state·해시 nonce를 실은 인가 주소를 만든다")
    void buildsFormPostAuthorizationUri() {
        HashedNonce nonce = HashedNonce.fromRaw("raw-nonce");
        AppleWebAuthorizationAdapter adapter = new AppleWebAuthorizationAdapter(WEB, OIDC);

        URI uri = adapter.authorizationUri("state-123", nonce);

        assertThat(uri)
                .hasScheme("https")
                .hasHost("appleid.apple.com")
                .hasPath("/auth/authorize")
                .hasParameter("response_type", "code id_token")
                .hasParameter("response_mode", "form_post")
                .hasParameter("client_id", "com.orbit.web")
                .hasParameter("redirect_uri", "https://api.example.com/api/v1/auth/apple/callback")
                .hasParameter("scope", "name email")
                .hasParameter("state", "state-123")
                .hasParameter("nonce", nonce.value());
        assertThat(adapter.clientId()).isEqualTo("com.orbit.web");
        assertThat(adapter.redirectUri()).isEqualTo("https://api.example.com/api/v1/auth/apple/callback");
    }

    @Test
    @DisplayName("웹 Services ID가 id_token 허용 목록에 없으면 기동하지 않는다")
    void requiresServicesIdInAllowedAudiences() {
        AppleWebLoginProperties unknownClient = new AppleWebLoginProperties(
                "https://appleid.apple.com/auth/authorize", "com.other.web", "https://api.example.com/callback");

        assertThatThrownBy(() -> new AppleWebAuthorizationAdapter(unknownClient, OIDC))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
