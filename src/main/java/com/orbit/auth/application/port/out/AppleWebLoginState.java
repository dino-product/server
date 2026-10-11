package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.HashedNonce;
import com.orbit.auth.domain.PkceChallenge;

/** 진행 중인 Apple 웹 로그인. 인가 요청에 실은 해시 nonce, 끝나면 돌아갈 클라이언트 주소, 교환 코드를 묶을 PKCE challenge다. */
public record AppleWebLoginState(HashedNonce nonce, String returnUri, PkceChallenge codeChallenge) {}
