package com.orbit.realtime.adapter.in.web;

import jakarta.servlet.DispatcherType;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;
import org.springframework.stereotype.Component;

import com.orbit.shared.security.SecurityFilterChainCustomizer;

/**
 * 실시간 연결의 비동기 재디스패치를 인가 검사에서 뺀다. 연결이 끝나면(완료·오류·시간 초과) 컨테이너가 같은 요청을 비동기로 다시 디스패치하는데, Bearer 인증은 최초
 * 요청에서만 하므로 재디스패치는 익명이 되어 이미 보낸 응답에 401을 쓰려다 실패한다. 최초 요청은 그대로 인증·인가를 거친다.
 */
@Component
class RealtimeSecurityCustomizer implements SecurityFilterChainCustomizer {

    @Override
    public void customize(HttpSecurity http) {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(new AndRequestMatcher(
                        new DispatcherTypeRequestMatcher(DispatcherType.ASYNC),
                        PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.GET, RealtimeStreamController.PATH_PATTERN)))
                .permitAll());
    }
}
