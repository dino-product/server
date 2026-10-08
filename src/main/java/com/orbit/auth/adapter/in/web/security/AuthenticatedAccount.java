package com.orbit.auth.adapter.in.web.security;

import java.time.Instant;

import com.orbit.auth.AccountPrincipal;

/**
 * 인증 필터가 SecurityContext에 넣는 principal. 로그아웃·내 계정 조회처럼 토큰 식별자·만료 시각이 필요한 auth 컨트롤러는 이 타입으로 받고, 다른 모듈은
 * 루트 공개 계약 {@link AccountPrincipal}로 받는다.
 */
public record AuthenticatedAccount(Long accountId, String tokenId, Instant tokenExpiresAt) implements AccountPrincipal {

    /** principal을 로그에 남겨도 토큰 식별자가 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "AuthenticatedAccount[accountId=" + accountId + ", tokenExpiresAt=" + tokenExpiresAt + "]";
    }
}
