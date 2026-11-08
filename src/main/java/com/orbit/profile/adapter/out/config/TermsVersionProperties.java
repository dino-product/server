package com.orbit.profile.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시행 중인 약관 버전(시행일 YYYY-MM-DD). 필수 약관(서비스 이용약관·개인정보 수집·이용 동의)의 값을 올리면 기존 계정은 다음 요청부터 재동의 전까지 이용이 막힌다. 개인정보 처리방침은 동의
 * 대상이 아니라 본문 주소에만 쓴다.
 */
@ConfigurationProperties(prefix = "app.profile.terms.versions")
public record TermsVersionProperties(String service, String privacy, String marketing, String privacyPolicy) {}
