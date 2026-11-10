package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("refresh token 해시와 세션 식별자")
class RefreshTokenHashTest {

    @Test
    @DisplayName("비밀값의 UTF-8 SHA-256을 소문자 hex로 만든다")
    void hashesSecretWithSha256() {
        assertThat(RefreshTokenHash.of("abc").value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("빈 비밀값은 해시하지 않는다")
    void rejectsBlankSecret(String secret) {
        assertThatThrownBy(() -> RefreshTokenHash.of(secret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secret must not be blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015a"
            })
    @DisplayName("소문자 hex 64자가 아닌 해시는 거부한다")
    void rejectsMalformedHash(String value) {
        assertThatThrownBy(() -> new RefreshTokenHash(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh token hash must be 64 lowercase hex characters");
    }

    @Test
    @DisplayName("세션 식별자는 소문자 UUID만 받는다")
    void acceptsOnlyLowercaseUuidSessionId() {
        assertThat(new RefreshSessionId("7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b").value())
                .isEqualTo("7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b");
        assertThatThrownBy(() -> new RefreshSessionId("7F1C2B3A-4D5E-4F60-8A9B-0C1D2E3F4A5B"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh session id must be a lowercase UUID");
        assertThatThrownBy(() -> new RefreshSessionId("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh session id must be a lowercase UUID");
        assertThatThrownBy(() -> new RefreshSessionId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh session id must be a lowercase UUID");
    }
}
