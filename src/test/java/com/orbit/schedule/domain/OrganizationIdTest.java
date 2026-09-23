package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("조직 식별자")
class OrganizationIdTest {

    @Test
    @DisplayName("양수 값으로 생성된다")
    void createsWithPositiveValue() {
        OrganizationId organizationId = new OrganizationId(1L);

        assertThat(organizationId.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null 값은 거부한다")
    void rejectsNullValue() {
        assertThatThrownBy(() -> new OrganizationId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must be positive");
    }

    @Test
    @DisplayName("0은 거부한다")
    void rejectsZeroValue() {
        assertThatThrownBy(() -> new OrganizationId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must be positive");
    }

    @Test
    @DisplayName("음수는 거부한다")
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> new OrganizationId(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizationId must be positive");
    }
}
