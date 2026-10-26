package com.orbit.auth.adapter.out.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오 관리자 API(연결 끊기) 주소, 서비스 앱 어드민 키, 호출 제한 시간. 어드민 키는 서버에서만 쓰며 문자열 표현에서 가린다. */
@ConfigurationProperties(prefix = "app.auth.kakao.admin-api")
public record KakaoAdminApiProperties(String unlinkUri, String adminKey, Duration timeout) {

    public KakaoAdminApiProperties {
        if (unlinkUri == null || unlinkUri.isBlank()) {
            throw new IllegalArgumentException("app.auth.kakao.admin-api.unlink-uri must not be blank");
        }
        if (adminKey == null || adminKey.isBlank()) {
            throw new IllegalArgumentException("app.auth.kakao.admin-api.admin-key must not be blank");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.auth.kakao.admin-api.timeout must be positive");
        }
    }

    @Override
    public String toString() {
        return "KakaoAdminApiProperties[unlinkUri=" + unlinkUri + ", adminKey=***, timeout=" + timeout + "]";
    }
}
