package com.orbit.auth.adapter.in.web.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;

import com.orbit.auth.application.port.in.query.AuthenticateAccessTokenUseCase;
import com.orbit.shared.security.ApiErrorResponseWriter;
import com.orbit.shared.security.SecurityFilterChainCustomizer;

/**
 * 카카오·Apple 로그인 진입 경로와 토큰 재발급·로그아웃 경로를 공개하고 Bearer 인증 필터를 익명 처리 앞에 끼운다. 로그인과 refresh token 자체가 인증
 * 수단이므로 이 경로들은 Access Token 없이 호출한다. 로그아웃은 Access Token이 만료된 기기도 refresh token으로 부를 수 있어야 한다.
 */
@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    static final String KAKAO_LOGIN_PATHS = "/api/v1/auth/kakao/**";
    static final String APPLE_LOGIN_PATHS = "/api/v1/auth/apple/**";
    static final String REFRESH_PATH = "/api/v1/auth/refresh";
    static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final AccessTokenAuthenticationFilter authenticationFilter;

    AuthSecurityCustomizer(AuthenticateAccessTokenUseCase useCase, ApiErrorResponseWriter responseWriter) {
        this.authenticationFilter = new AccessTokenAuthenticationFilter(useCase, responseWriter);
    }

    @Override
    public void customize(HttpSecurity http) {
        http.addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(KAKAO_LOGIN_PATHS, APPLE_LOGIN_PATHS, REFRESH_PATH, LOGOUT_PATH)
                        .permitAll());
    }
}
