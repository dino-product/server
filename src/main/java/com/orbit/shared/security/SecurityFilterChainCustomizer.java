package com.orbit.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * 비즈니스 모듈이 공통 SecurityFilterChain에 인증 필터·공개 경로를 덧붙이는 확장점. shared의 SecurityConfig가 모든 구현을 공통 허용 경로와
 * {@code anyRequest().authenticated()}보다 먼저 적용한다.
 */
@FunctionalInterface
public interface SecurityFilterChainCustomizer {

    void customize(HttpSecurity http);
}
