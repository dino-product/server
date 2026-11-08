package com.orbit.profile.adapter.in.web;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

import com.orbit.shared.security.SecurityFilterChainCustomizer;

/** 시행 중인 약관 조회는 로그인 화면·앱 설정의 약관 보기에서도 쓰므로 Access Token 없이 연다. */
@Component
class ProfileSecurityCustomizer implements SecurityFilterChainCustomizer {

    @Override
    public void customize(HttpSecurity http) {
        http.authorizeHttpRequests(authorize ->
                authorize.requestMatchers(HttpMethod.GET, TermsController.PATH).permitAll());
    }
}
