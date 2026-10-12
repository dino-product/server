package com.orbit.auth.adapter.out.apple;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apple OIDC 발급자·JWKS 주소·허용 대상 설정. iOS 앱은 Bundle ID, 웹·Android는 Services ID를 id_token {@code aud}로 받으므로 대상은 목록으로 받는다.
 * Apple {@code sub}는 개발자 팀 단위로 같으므로 같은 팀의 클라이언트들이 한 계정으로 합쳐지는 것이 정상이다.
 */
@ConfigurationProperties(prefix = "app.auth.apple")
public record AppleOidcProperties(String issuer, String jwkSetUri, List<String> allowedAudiences) {

    public AppleOidcProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.issuer must not be blank");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.jwk-set-uri must not be blank");
        }
        if (allowedAudiences == null
                || allowedAudiences.isEmpty()
                || allowedAudiences.stream().anyMatch(audience -> audience == null || audience.isBlank())) {
            throw new IllegalArgumentException("app.auth.apple.allowed-audiences must contain client ids");
        }
        allowedAudiences = List.copyOf(allowedAudiences);
    }
}
