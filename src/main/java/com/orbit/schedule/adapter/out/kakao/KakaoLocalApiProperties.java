package com.orbit.schedule.adapter.out.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API(주소 검색) 주소, 카카오 앱 REST API 키, 호출 제한 시간. 키는 웹 카카오 로그인과 같은 앱의 REST API 키이며 문자열 표현에서 가린다. 제한
 * 시간은 작업 저장을 붙잡는 시간이라 짧게 둔다.
 */
@ConfigurationProperties(prefix = "app.schedule.geocoding.kakao")
public record KakaoLocalApiProperties(String addressSearchUri, String restApiKey, Duration timeout) {

    public KakaoLocalApiProperties {
        if (addressSearchUri == null || addressSearchUri.isBlank()) {
            throw new IllegalArgumentException("app.schedule.geocoding.kakao.address-search-uri must not be blank");
        }
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalArgumentException("app.schedule.geocoding.kakao.rest-api-key must not be blank");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.schedule.geocoding.kakao.timeout must be positive");
        }
    }

    @Override
    public String toString() {
        return "KakaoLocalApiProperties[addressSearchUri=" + addressSearchUri + ", restApiKey=***, timeout=" + timeout
                + "]";
    }
}
