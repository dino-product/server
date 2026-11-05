package com.orbit.schedule.adapter.in.web.board;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 진행 보드 조회의 동시 처리 상한을 다른 인터셉터보다 먼저 건다. 가입 공통 검사(profile, 기본 순서)는 DB를 쓰므로 그보다 앞서야 한다. */
@Configuration
class ProgressBoardWebConfig implements WebMvcConfigurer {

    private final ProgressBoardProperties properties;

    ProgressBoardWebConfig(ProgressBoardProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ProgressBoardConcurrencyLimitInterceptor(properties))
                .addPathPatterns(ProgressBoardConcurrencyLimitInterceptor.PATH_PATTERN)
                .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
