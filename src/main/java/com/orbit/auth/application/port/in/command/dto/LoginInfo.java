package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

/** 로그인 결과. {@code registered}는 이번 로그인으로 계정이 처음 만들어졌는지를 뜻한다. */
public record LoginInfo(Long accountId, boolean registered, String accessToken, Instant accessTokenExpiresAt) {}
