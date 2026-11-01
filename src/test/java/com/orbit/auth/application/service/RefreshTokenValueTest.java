package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.orbit.auth.domain.RefreshSessionId;

@DisplayName("refresh token 문자열")
class RefreshTokenValueTest {

    private static final String SESSION = "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b";
    private static final String SECRET = "Ab0_-".repeat(8) + "xyz";

    @Test
    @DisplayName("세션 식별자와 비밀값을 점으로 이어 만들고 다시 읽는다")
    void formatsAndParses() {
        RefreshTokenValue value = new RefreshTokenValue(new RefreshSessionId(SESSION), SECRET);

        assertThat(value.format()).isEqualTo(SESSION + "." + SECRET);
        assertThat(RefreshTokenValue.parse(value.format())).contains(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "no-dot",
                "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.",
                "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.short",
                "7F1C2B3A-4D5E-4F60-8A9B-0C1D2E3F4A5B.Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-xyz",
                "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-xy=",
                "7f1c2b3a-4d5e-4f60-8a9b-0c1d2e3f4a5b.Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-xyz.extra",
                "not-a-uuid.Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-Ab0_-xyz"
            })
    @DisplayName("형식이 어긋나면 빈 값이다")
    void rejectsMalformedValue(String value) {
        assertThat(RefreshTokenValue.parse(value)).isEmpty();
    }

    @Test
    @DisplayName("문자열 표현에 비밀값을 남기지 않는다")
    void hidesSecretInToString() {
        assertThat(new RefreshTokenValue(new RefreshSessionId(SESSION), SECRET).toString())
                .doesNotContain(SECRET)
                .contains(SESSION);
    }
}
