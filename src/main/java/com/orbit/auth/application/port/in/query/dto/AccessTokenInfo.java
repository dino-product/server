package com.orbit.auth.application.port.in.query.dto;

import java.time.Instant;

/**
 * 검증·폐기 확인을 통과한 Access Token의 계정과 식별자. 로그아웃은 이 식별자로 토큰을 폐기하고 {@code sessionId}의 기기 세션을 끝낸다. refresh token
 * 도입 전에 발급된 토큰은 {@code sessionId}가 없다(null).
 */
public record AccessTokenInfo(Long accountId, String tokenId, Instant expiresAt, String sessionId) {}
