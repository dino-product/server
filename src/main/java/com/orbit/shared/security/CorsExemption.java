package com.orbit.shared.security;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 비즈니스 모듈이 공통 CORS 검사에서 뺄 요청을 등록하는 확장점. 다른 사이트가 브라우저를 이동시켜 보내는 요청(OAuth {@code form_post} 콜백 등)처럼
 * 브라우저가 CORS를 적용하지 않는데 Origin 헤더가 붙는 요청만 등록한다. shared의 SecurityConfig는 등록한 요청에 CORS 설정을 주지 않으므로 Origin으로
 * 거절하지 않고, CORS 허용 헤더도 붙이지 않아 다른 출처의 스크립트는 응답을 읽을 수 없다. 위조 요청 방어는 등록한 모듈이 맡는다.
 */
@FunctionalInterface
public interface CorsExemption {

    RequestMatcher matcher();
}
