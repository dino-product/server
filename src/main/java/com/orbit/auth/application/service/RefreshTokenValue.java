package com.orbit.auth.application.service;

import java.util.Optional;
import java.util.regex.Pattern;

import com.orbit.auth.domain.RefreshSessionId;

/**
 * 클라이언트가 보관하는 refresh token 문자열 {@code <세션 식별자>.<비밀값>}. 세션 식별자로 저장된 세션을 찾고 비밀값의 해시로 대조한다. 문자열 표현에
 * 비밀값을 남기지 않는다.
 */
record RefreshTokenValue(RefreshSessionId sessionId, String secret) {

    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int SESSION_ID_LENGTH = 36;

    RefreshTokenValue {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (secret == null || !SECRET.matcher(secret).matches()) {
            throw new IllegalArgumentException("refresh token secret must be 43 base64url characters");
        }
    }

    /** 형식이 어긋나면 빈 값이다. */
    static Optional<RefreshTokenValue> parse(String value) {
        if (value == null || value.length() != SESSION_ID_LENGTH + 1 + 43 || value.charAt(SESSION_ID_LENGTH) != '.') {
            return Optional.empty();
        }
        try {
            return Optional.of(new RefreshTokenValue(
                    new RefreshSessionId(value.substring(0, SESSION_ID_LENGTH)),
                    value.substring(SESSION_ID_LENGTH + 1)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    String format() {
        return sessionId.value() + "." + secret;
    }

    @Override
    public String toString() {
        return "RefreshTokenValue[sessionId=" + sessionId.value() + "]";
    }
}
