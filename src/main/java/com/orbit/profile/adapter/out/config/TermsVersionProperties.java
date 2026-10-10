package com.orbit.profile.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 시행 중인 약관 버전. 필수 약관(서비스·개인정보)의 값을 올리면 기존 계정은 다음 요청부터 재동의 전까지 이용이 막힌다. */
@ConfigurationProperties(prefix = "app.profile.terms.versions")
public record TermsVersionProperties(String service, String privacy, String marketing) {}
