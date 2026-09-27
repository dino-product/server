package com.orbit.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 서버가 발급한 Access Token의 클레임. 토큰 문자열 자체는 발급 어댑터가 만들고 여기서는 식별자·계정·유효 구간의 정합성만 보장한다. 로그아웃 폐기는 남은
 * 유효 시간 동안만 기억하면 되므로 만료 이후에는 검증기가 먼저 거부한다.
 */
public record AccessToken(String tokenId, AccountId accountId, Instant issuedAt, Instant expiresAt) {

    public AccessToken {
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId must not be blank");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("issuedAt and expiresAt must not be null");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    /** 지금부터 만료까지 남은 시간. 이미 만료됐으면 0이다. */
    public Duration remainingLifetimeAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
