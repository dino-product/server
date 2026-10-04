package com.orbit.auth.domain;

/**
 * 외부 제공자가 발급한 사용자 식별(OIDC {@code sub}). 제공자 안에서만 유일하며 값은 제공자가 준 그대로 보관한다. 이메일·닉네임 같은 프로필 클레임은
 * 식별에 쓰지 않는다.
 */
public record ExternalIdentity(OAuthProvider provider, String subject) {

    private static final int MAX_SUBJECT_LENGTH = 64;

    public ExternalIdentity {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("subject must be at most " + MAX_SUBJECT_LENGTH + " characters");
        }
    }
}
