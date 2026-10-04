package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/** 인증 필터가 확인한 현재 Access Token의 식별자와 만료 시각. */
public record LogoutCommand(String tokenId, Instant tokenExpiresAt) {}
