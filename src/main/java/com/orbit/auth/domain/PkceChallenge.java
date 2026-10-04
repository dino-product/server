package com.orbit.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PKCE(RFC 7636) S256 code_challenge. 로그인을 시작한 클라이언트만 아는 code_verifier의 SHA-256을 base64url(패딩 없음)로 적은 값이며, 콜백 뒤 교환
 * 코드를 그 클라이언트만 쓸 수 있게 묶는다.
 */
public record PkceChallenge(String value) {

    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    public PkceChallenge {
        if (value == null || !CHALLENGE.matcher(value).matches()) {
            throw new IllegalArgumentException("code_challenge must be a 43-character base64url S256 value");
        }
    }

    public static Optional<PkceChallenge> parse(String value) {
        return value != null && CHALLENGE.matcher(value).matches()
                ? Optional.of(new PkceChallenge(value))
                : Optional.empty();
    }

    /** code_verifier가 형식(43~128자의 unreserved 문자)에 맞고 그 S256 값이 이 challenge와 같은지 일정 시간에 비교한다. */
    public boolean matches(String verifier) {
        if (verifier == null || !VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII), value.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sha256(String verifier) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
