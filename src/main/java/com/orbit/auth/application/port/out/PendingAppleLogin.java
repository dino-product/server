package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.PkceChallenge;

/**
 * 웹·Android Apple 콜백을 마치고 클라이언트가 교환 코드로 Access Token을 받아 가기를 기다리는 로그인. 로그인을 시작한 클라이언트의 code_verifier만
 * {@code codeChallenge}와 맞는다.
 */
public record PendingAppleLogin(AccountId accountId, boolean registered, PkceChallenge codeChallenge) {}
