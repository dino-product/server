package com.orbit.auth.domain;

import java.util.regex.Pattern;

/**
 * 기기 세션 식별자(소문자 UUID). refresh token 앞부분과 Access Token의 {@code sid} 클레임에 실린다. 추측할 수 없어야 하므로 발급 쪽이 무작위 UUID로
 * 만든다.
 */
public record RefreshSessionId(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public RefreshSessionId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("refresh session id must be a lowercase UUID");
        }
    }
}
