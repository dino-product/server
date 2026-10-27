package com.orbit.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** refresh token 비밀값의 SHA-256 해시(소문자 hex 64자). 저장소에는 원문 대신 이 값만 둔다. */
public record RefreshTokenHash(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public RefreshTokenHash {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("refresh token hash must be 64 lowercase hex characters");
        }
    }

    /** 비밀값의 UTF-8 바이트를 SHA-256으로 해시한다. */
    public static RefreshTokenHash of(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        return new RefreshTokenHash(HexFormat.of().formatHex(sha256(secret.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
