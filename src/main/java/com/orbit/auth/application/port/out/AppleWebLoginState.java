package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.HashedNonce;

/**
 * 진행 중인 Apple 웹 로그인. 인가 요청에 실은 해시 nonce, 시작한 브라우저에 준 연결 값의 SHA-256 hex, 끝나면 돌아갈 클라이언트 주소를 state에 묶는다.
 */
public record AppleWebLoginState(HashedNonce nonce, String browserBindingHash, String returnUri) {}
