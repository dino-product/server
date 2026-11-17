package com.orbit.auth.adapter.in.web.security;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import com.orbit.shared.security.CorsExemption;

/**
 * Apple 웹 로그인 콜백을 CORS 검사에서 뺀다. Apple은 로그인 화면에서 브라우저를 이 경로로 이동시키는 {@code form_post}로 콜백을 보내므로 Origin이
 * {@code https://appleid.apple.com}(또는 {@code null})이고, CORS 허용 목록과 대조하면 콜백이 처리되지 않는다. 위조 콜백은 state·시작 브라우저
 * 연결 쿠키·nonce 검증이 막는다. 사전 요청(OPTIONS)도 함께 빼서 허용한 출처의 스크립트에도 콜백 응답을 열지 않는다.
 */
@Component
class AppleCallbackCorsExemption implements CorsExemption {

    private static final String CALLBACK_PATH = "/api/v1/auth/apple/callback";

    @Override
    public RequestMatcher matcher() {
        return PathPatternRequestMatcher.withDefaults().matcher(CALLBACK_PATH);
    }
}
