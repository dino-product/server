package com.orbit.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("연락처")
class PhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "010-1234-5678", " 010 1234 5678 "})
    @DisplayName("하이픈·공백을 지운 숫자 11자리가 010으로 시작하면 숫자만 저장한다")
    void storesDigitsOnly(String value) {
        assertThat(new PhoneNumber(value).value()).isEqualTo("01012345678");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "0101234567",
                "010123456789",
                "01112345678",
                "011-1234-5678",
                "010-1234-567a",
                "+821012345678"
            })
    @DisplayName("비었거나 010으로 시작하는 11자리가 아니거나 숫자·하이픈·공백 외 문자가 있으면 거부한다")
    void rejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> new PhoneNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
