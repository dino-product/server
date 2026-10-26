package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("참여 요청 거절 사유")
class RejectReasonTest {

    @Test
    @DisplayName("앞뒤 공백을 지운 사유를 남긴다")
    void stripsSurroundingWhitespace() {
        assertThat(RejectReason.from("  인원 마감\n")).map(RejectReason::value).contains("인원 마감");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("비었거나 공백뿐이면 사유 없음이다")
    void treatsBlankAsNoReason(String raw) {
        assertThat(RejectReason.from(raw)).isEmpty();
    }

    @Test
    @DisplayName("255자까지 허용하고 글자 수는 코드 포인트로 센다")
    void allowsUpToMaxLengthInCodePoints() {
        String emojiReason = "😀".repeat(RejectReason.MAX_LENGTH);

        assertThat(RejectReason.from("가".repeat(RejectReason.MAX_LENGTH))).isPresent();
        assertThat(RejectReason.from(emojiReason)).map(RejectReason::value).contains(emojiReason);
    }

    @Test
    @DisplayName("255자를 넘으면 거부한다")
    void refusesTooLongReason() {
        assertThatThrownBy(() -> RejectReason.from("가".repeat(RejectReason.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장할 수 없는 NUL 문자는 거부한다")
    void refusesNulCharacter() {
        assertThatThrownBy(() -> RejectReason.from("인원\u0000마감")).isInstanceOf(IllegalArgumentException.class);
    }
}
