package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/** 인증 필터가 확인한 현재 Access Token의 계정·식별자·만료 시각과 그 토큰을 발급한 기기 세션(도입 전 토큰은 null). */
public record LogoutCommand(Long accountId, String tokenId, Instant tokenExpiresAt, String sessionId) {}
