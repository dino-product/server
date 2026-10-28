package com.orbit.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("앱 푸시 기기")
class PushDeviceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Test
    @DisplayName("비었거나 너무 긴 토큰은 기기로 만들지 않는다")
    void rejectsBlankOrTooLongToken() {
        assertThatThrownBy(() -> PushDevice.register(7L, PushPlatform.IOS, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PushDevice.register(7L, PushPlatform.IOS, "a".repeat(PushDevice.MAX_TOKEN_LENGTH + 1), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(PushDevice.register(7L, PushPlatform.IOS, "a".repeat(PushDevice.MAX_TOKEN_LENGTH), NOW)
                        .token())
                .hasSize(PushDevice.MAX_TOKEN_LENGTH);
    }

    @Test
    @DisplayName("계정·플랫폼·등록 시각이 없으면 기기로 만들지 않는다")
    void rejectsMissingOwnerPlatformOrTime() {
        assertThatThrownBy(() -> PushDevice.register(null, PushPlatform.IOS, "token", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushDevice.register(7L, null, "token", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushDevice.register(7L, PushPlatform.IOS, "token", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열 표현에는 토큰의 앞 여섯 글자와 길이만 남긴다")
    void masksTokenInStringForm() {
        String token = "abcdefSECRET-REST-OF-TOKEN";
        PushDevice device = PushDevice.reconstitute(3L, 7L, PushPlatform.ANDROID, token, NOW);

        assertThat(device.maskedToken()).isEqualTo("abcdef...(" + token.length() + ")");
        assertThat(device.toString()).doesNotContain("SECRET").contains(device.maskedToken());
        assertThat(PushDevice.register(7L, PushPlatform.ANDROID, "abc", NOW).maskedToken())
                .isEqualTo("abc...(3)");
    }
}
