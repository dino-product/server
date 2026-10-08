package com.orbit.auth;

/**
 * 인증된 요청의 계정. 다른 모듈의 웹 어댑터는 {@code @AuthenticationPrincipal}로 받아 계정 식별자를 Command·Query에 넘긴다.
 *
 * <p>Access Token 검증을 통과한 요청에만 있다. 공개 경로처럼 인증되지 않은 요청에서는 {@code @AuthenticationPrincipal}이 {@code null}을
 * 넘기므로 인증이 필요한 경로에서만 받는다. 토큰 식별자·만료 시각처럼 auth 안에서만 쓰는 값은 드러내지 않는다.
 */
public interface AccountPrincipal {

    Long accountId();
}
