package com.orbit.auth.adapter.in.web.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

import com.orbit.shared.security.SecurityFilterChainCustomizer;

/** 카카오 로그인 진입 경로를 공개한다. 로그인 자체가 인증 수단이므로 이 경로는 토큰 없이 호출한다. */
@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    static final String KAKAO_LOGIN_PATHS = "/api/v1/auth/kakao/**";

    @Override
    public void customize(HttpSecurity http) {
        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(KAKAO_LOGIN_PATHS).permitAll());
    }
}
