package com.orbit.auth.adapter.in.web.security;

import java.time.Instant;

/** 인증 필터가 SecurityContext에 넣는 principal. 컨트롤러는 {@code @AuthenticationPrincipal}로 받는다. */
public record AuthenticatedAccount(Long accountId, String tokenId, Instant tokenExpiresAt) {}
