package com.orbit.auth.adapter.in.web.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.shared.security.ApiErrorResponseWriter;
import com.orbit.shared.security.SecurityFilterChainCustomizer;

/** 카카오·Apple 로그인 진입 경로를 공개하고 Bearer 인증 필터를 익명 처리 앞에 끼운다. 로그인 자체가 인증 수단이므로 로그인 경로는 토큰 없이 호출한다. */
@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    static final String KAKAO_LOGIN_PATHS = "/api/v1/auth/kakao/**";
    static final String APPLE_LOGIN_PATHS = "/api/v1/auth/apple/**";

    private final AccessTokenAuthenticationFilter authenticationFilter;

    AuthSecurityCustomizer(AuthenticateAccessTokenUseCase useCase, ApiErrorResponseWriter responseWriter) {
        this.authenticationFilter = new AccessTokenAuthenticationFilter(useCase, responseWriter);
    }

    @Override
    public void customize(HttpSecurity http) {
        http.addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(KAKAO_LOGIN_PATHS, APPLE_LOGIN_PATHS)
                        .permitAll());
    }
}
