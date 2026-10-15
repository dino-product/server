package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("회사 코드")
class CompanyCodeTest {

    @Test
    @DisplayName("Crockford Base32 문자 6자는 회사 코드다")
    void acceptsSixCrockfordBase32Characters() {
        assertThat(new CompanyCode("7K2M9X").value()).isEqualTo("7K2M9X");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"7K2M9", "7K2M9XA"})
    @DisplayName("6자가 아니면 거부한다")
    void rejectsLengthOtherThanSix(String value) {
        assertThatThrownBy(() -> new CompanyCode(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7K2M9I", "7K2M9L", "7K2M9O", "7K2M9U", "7k2m9x", "7K2-9X"})
    @DisplayName("I·L·O·U, 소문자, 기호는 저장 형식으로 거부한다")
    void rejectsCharactersOutsideCrockfordBase32(String value) {
        assertThatThrownBy(() -> new CompanyCode(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7k2m9x", " 7K2M9X ", "7K2-M9X", "7k 2m-9x"})
    @DisplayName("입력은 대소문자를 무시하고 공백·하이픈을 지워 읽는다")
    void parsesInputIgnoringCaseSpacesAndHyphens(String input) {
        assertThat(CompanyCode.parse(input)).isEqualTo(new CompanyCode("7K2M9X"));
    }

    @Test
    @DisplayName("정규화한 뒤에도 형식이 맞지 않는 입력은 거부한다")
    void rejectsInputInvalidAfterNormalization() {
        assertThatThrownBy(() -> CompanyCode.parse("ORBIT-7K2M")).isInstanceOf(IllegalArgumentException.class);
    }
}
