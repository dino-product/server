package com.orbit.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 로그인 nonce의 SHA-256 해시(소문자 hex 64자). Apple은 앱이 요청에 넣은 nonce를 id_token에 그대로 싣고, 앱은 서버가 준 raw nonce의 해시를 넣으므로
 * 서버는 해시로 보관하고 id_token의 nonce 클레임과 대조한다. raw nonce를 그대로 넣은 토큰은 해시와 맞지 않아 거부된다.
 */
public record HashedNonce(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public HashedNonce {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("hashed nonce must be 64 lowercase hex characters");
        }
    }

    /** 서버가 발급한 raw nonce의 UTF-8 바이트를 SHA-256으로 해시한다. */
    public static HashedNonce fromRaw(String rawNonce) {
        if (rawNonce == null || rawNonce.isBlank()) {
            throw new IllegalArgumentException("raw nonce must not be blank");
        }
        return new HashedNonce(HexFormat.of().formatHex(sha256(rawNonce.getBytes(StandardCharsets.UTF_8))));
    }

    /** id_token의 nonce 클레임을 해시로 읽는다. hex 대소문자는 구분하지 않으며 해시 형식이 아니면 빈 값이다. */
    public static Optional<HashedNonce> fromClaim(String claim) {
        if (claim == null) {
            return Optional.empty();
        }
        String normalized = claim.toLowerCase(Locale.ROOT);
        return FORMAT.matcher(normalized).matches() ? Optional.of(new HashedNonce(normalized)) : Optional.empty();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
