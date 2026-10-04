package com.orbit.auth.application.port.out;

import java.time.Duration;

/** 로그아웃으로 폐기한 Access Token 식별자를 남은 유효 시간 동안 기억한다. 만료 뒤에는 검증기가 먼저 거부하므로 더 오래 보관하지 않는다. */
public interface RevokedAccessTokenPort {

    void revoke(String tokenId, Duration remainingLifetime);

    boolean isRevoked(String tokenId);
}
