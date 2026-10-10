package com.orbit.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("이름")
class PersonNameTest {

    @Test
    @DisplayName("앞뒤 공백을 지운 값으로 저장한다")
    void stripsSurroundingSpaces() {
        assertThat(new PersonName("  홍길동 ").value()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("한글과 영문자는 2자부터 20자까지 받는다")
    void acceptsHangulAndLatinWithinLength() {
        assertThat(new PersonName("홍길").value()).isEqualTo("홍길");
        assertThat(new PersonName("가".repeat(20)).value()).hasSize(20);
        assertThat(new PersonName("Kim").value()).isEqualTo("Kim");
        assertThat(new PersonName("김Lee").value()).isEqualTo("김Lee");
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 뒤 1자이거나 21자이면 거부한다")
    void rejectsLengthOutsideRange() {
        assertThatThrownBy(() -> new PersonName(" 가 ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonName("가".repeat(21))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("비었거나 공백만 있으면 입력하지 않은 것으로 보고 거부한다")
    void rejectsMissingName(String value) {
        assertThatThrownBy(() -> new PersonName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동1", "홍_길동", "홍길동!", "홍 길동", "ㅋㅋ", "홍길동😀", "홍길동©", "Kim☀"})
    @DisplayName("숫자·특수문자·공백·자모·이모지가 섞이면 거부한다")
    void rejectsCharactersOtherThanHangulAndLatin(String value) {
        assertThatThrownBy(() -> new PersonName(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
