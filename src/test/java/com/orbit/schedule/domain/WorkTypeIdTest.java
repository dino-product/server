package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 유형 식별자")
class WorkTypeIdTest {

    @Test
    @DisplayName("양수 값으로 생성된다")
    void createsWithPositiveValue() {
        WorkTypeId workTypeId = new WorkTypeId(1L);

        assertThat(workTypeId.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null 값은 거부한다")
    void rejectsNullValue() {
        assertThatThrownBy(() -> new WorkTypeId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workTypeId must be positive");
    }

    @Test
    @DisplayName("0은 거부한다")
    void rejectsZeroValue() {
        assertThatThrownBy(() -> new WorkTypeId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workTypeId must be positive");
    }

    @Test
    @DisplayName("음수는 거부한다")
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> new WorkTypeId(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workTypeId must be positive");
    }
}
