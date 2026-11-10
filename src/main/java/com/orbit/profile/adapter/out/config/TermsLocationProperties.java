package com.orbit.profile.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 약관 본문 txt가 있는 주소의 앞부분. 본문은 {@code {baseUrl}/{종류}/{버전}.txt}에 있다(웹 CloudFront의 {@code /terms/}). */
@ConfigurationProperties(prefix = "app.profile.terms")
public record TermsLocationProperties(String baseUrl) {}
