package com.orbit.organization.adapter.out.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.domain.CompanyCode;

@DisplayName("무작위 회사 코드 발급기")
class SecureRandomCompanyCodeGeneratorTest {

    private final SecureRandomCompanyCodeGenerator generator = new SecureRandomCompanyCodeGenerator();

    @Test
    @DisplayName("발급한 코드는 모두 회사 코드 형식이고 Crockford Base32 문자 전체를 고르게 쓴다")
    void generatesValidCodesFromWholeAlphabet() {
        Set<Character> usedCharacters = new HashSet<>();

        IntStream.range(0, 2_000)
                .mapToObj(i -> generator.generate())
                .map(CompanyCode::value)
                .forEach(value -> value.chars().forEach(c -> usedCharacters.add((char) c)));

        assertThat(usedCharacters).hasSize(CompanyCode.ALPHABET.length());
    }
}
