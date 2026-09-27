package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeColorTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void acceptsEightTemporaryPresets(int value) {
        assertThat(new TypeColor(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9})
    void rejectsUnknownPresets(int value) {
        assertThatThrownBy(() -> new TypeColor(value)).isInstanceOf(OrganizationRuleViolation.class);
    }
}
