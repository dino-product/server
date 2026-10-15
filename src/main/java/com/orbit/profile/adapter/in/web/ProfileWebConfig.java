package com.orbit.profile.adapter.in.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.orbit.profile.application.port.in.query.CheckServiceAccessUseCase;

/**
 * 가입 완료 공통 검사를 모든 {@code /api/**}에 건다. 허용 목록은 프로필 입력·약관 동의·내 정보(profile)와 로그인·로그아웃·내 계정(auth)이다. 링크·QR로
 * 들어온 계정의 회사 확인 API(HM-289)와 탈퇴 API가 생기면 그 경로를 여기에 더한다.
 */
@Configuration
class ProfileWebConfig implements WebMvcConfigurer {

    static final String[] ALLOWED_BEFORE_SIGNUP = {"/api/v1/profiles/**", "/api/v1/auth/**"};

    private final CheckServiceAccessUseCase checkServiceAccessUseCase;

    ProfileWebConfig(CheckServiceAccessUseCase checkServiceAccessUseCase) {
        this.checkServiceAccessUseCase = checkServiceAccessUseCase;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SignupGateInterceptor(checkServiceAccessUseCase))
                .addPathPatterns("/api/**")
                .excludePathPatterns(ALLOWED_BEFORE_SIGNUP);
    }
}
