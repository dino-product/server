package com.orbit.auth.application.port.out;

import com.orbit.auth.domain.AccessToken;

/** 직렬화된 토큰 문자열과 그 안에 담긴 클레임. */
public record IssuedAccessToken(String value, AccessToken token) {}
