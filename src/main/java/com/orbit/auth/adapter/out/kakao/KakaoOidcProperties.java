package com.orbit.auth.adapter.out.kakao;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 OIDC 발급자·JWKS 주소·허용 대상(앱 키) 설정. 웹의 REST API 키와 앱의 네이티브 앱 키가 서로 다르므로 대상은 목록으로 받는다. 목록의 모든 키는 반드시
 * 하나의 카카오 앱에 속해야 한다. 카카오 {@code sub}는 앱마다 다른 회원번호이고 계정은 (provider, sub)로만 식별하므로, 다른 카카오 앱의 키를 섞으면 두 앱의
 * 같은 회원번호가 한 계정으로 합쳐진다.
 */
@ConfigurationProperties(prefix = "app.auth.kakao")
public record KakaoOidcProperties(String issuer, String jwkSetUri, List<String> allowedAudiences) {

    public KakaoOidcProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.auth.kakao.issuer must not be blank");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.kakao.jwk-set-uri must not be blank");
        }
        if (allowedAudiences == null
                || allowedAudiences.isEmpty()
                || allowedAudiences.stream().anyMatch(audience -> audience == null || audience.isBlank())) {
            throw new IllegalArgumentException("app.auth.kakao.allowed-audiences must contain app keys");
        }
        allowedAudiences = List.copyOf(allowedAudiences);
    }
}
