package com.orbit.auth.adapter.out.apple;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹·Android Apple 로그인 설정. {@code servicesId}는 Apple 개발자 콘솔의 Services ID(웹 client_id)이고, {@code redirectUri}는 그
 * Services ID에 등록한 이 서버의 콜백 주소({@code /api/v1/auth/apple/callback})다.
 */
@ConfigurationProperties(prefix = "app.auth.apple.web")
public record AppleWebLoginProperties(String authorizationUri, String servicesId, String redirectUri) {

    public AppleWebLoginProperties {
        if (authorizationUri == null || authorizationUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.web.authorization-uri must not be blank");
        }
        if (servicesId == null || servicesId.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.web.services-id must not be blank");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.apple.web.redirect-uri must not be blank");
        }
    }
}
