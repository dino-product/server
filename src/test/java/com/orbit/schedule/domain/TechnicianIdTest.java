package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("기사 식별자")
class TechnicianIdTest {

    @Test
    @DisplayName("양수 값으로 생성된다")
    void createsWithPositiveValue() {
        assertThat(new TechnicianId(1L).value()).isEqualTo(1L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("null·0·음수는 거부한다")
    void rejectsNonPositiveValue(Long value) {
        assertThatThrownBy(() -> new TechnicianId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("technicianId must be positive");
    }
}
