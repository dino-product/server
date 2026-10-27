package com.orbit.auth.adapter.out.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 카카오 로그인 설정. {@code restApiKey}는 앱 로그인과 같은 카카오 앱의 REST API 키, {@code clientSecret}은 그 키의 클라이언트 시크릿, {@code
 * redirectUri}는 카카오 앱에 등록한 이 서버의 콜백 주소({@code /api/v1/auth/kakao/callback})다. 클라이언트 시크릿은 서버에서만 쓰며 문자열 표현에서 가린다.
 */
@ConfigurationProperties(prefix = "app.auth.kakao.web")
public record KakaoWebLoginProperties(
        String authorizationUri,
        String tokenUri,
        String restApiKey,
        String clientSecret,
        String redirectUri,
        Duration timeout) {

    public KakaoWebLoginProperties {
        requireText(authorizationUri, "authorization-uri");
        requireText(tokenUri, "token-uri");
        requireText(restApiKey, "rest-api-key");
        requireText(clientSecret, "client-secret");
        requireText(redirectUri, "redirect-uri");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.auth.kakao.web.timeout must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.auth.kakao.web." + name + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "KakaoWebLoginProperties[authorizationUri=" + authorizationUri + ", tokenUri=" + tokenUri
                + ", restApiKey=" + restApiKey + ", clientSecret=***, redirectUri=" + redirectUri + ", timeout="
                + timeout + "]";
    }
}
