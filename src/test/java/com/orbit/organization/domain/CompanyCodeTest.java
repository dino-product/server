package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyCodeTest {
    @Test
    void reconstitutesLegacyCodeWithoutApplyingCurrentInputRule() {
        var restored = CompanyCode.reconstitute("legacy01");

        assertThat(restored.value()).isEqualTo("legacy01");
        assertThat(restored).isEqualTo(CompanyCode.reconstitute("legacy01"));
        assertThat(restored.hashCode())
                .isEqualTo(CompanyCode.reconstitute("legacy01").hashCode());
        assertThatThrownBy(() -> CompanyCode.reconstitute(null)).isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01234567", "89ABCDEF", "HJKMNPQR", "STVWXYZ0"})
    void acceptsEightCrockfordBase32CharactersWithoutChangingThem(String value) {
        assertThat(new CompanyCode(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "0123456",
                "012345678",
                "0123456I",
                "0123456L",
                "0123456O",
                "0123456U",
                "0123456i",
                "abcdefgh",
                "0123456-",
                "0123456 "
            })
    void rejectsOtherLengthsAndCharacters(String value) {
        assertThatThrownBy(() -> new CompanyCode(value)).isInstanceOf(OrganizationRuleViolation.class);
    }
}
