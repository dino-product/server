package com.orbit.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("탈퇴 사유")
class WithdrawalReasonTest {

    @Test
    @DisplayName("비워 두면 사유 없이 탈퇴한다")
    void treatsBlankAsNoReason() {
        assertThat(WithdrawalReason.from(null)).isEmpty();
        assertThat(WithdrawalReason.from("")).isEmpty();
        assertThat(WithdrawalReason.from("  \n ")).isEmpty();
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 자유 텍스트를 남긴다")
    void keepsStrippedText() {
        assertThat(WithdrawalReason.from("  더 이상 쓰지 않아요 "))
                .map(WithdrawalReason::value)
                .contains("더 이상 쓰지 않아요");
    }

    @Test
    @DisplayName("문자 200자까지 받고 넘으면 거부한다. 이모지처럼 두 자리로 저장되는 문자도 1자로 센다")
    void acceptsUpToTwoHundredCharacters() {
        String emoji = "😀";

        assertThat(WithdrawalReason.from("가".repeat(200))).isPresent();
        assertThat(WithdrawalReason.from(emoji.repeat(200))).isPresent();
        assertThatThrownBy(() -> WithdrawalReason.from("가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("withdrawal reason must be at most 200 characters");
    }
}
