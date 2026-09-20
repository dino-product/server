package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소속 식별자")
class MembershipIdTest {

    @Test
    @DisplayName("양수 값으로 생성된다")
    void createsWithPositiveValue() {
        MembershipId membershipId = new MembershipId(1L);

        assertThat(membershipId.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("null 값은 거부한다")
    void rejectsNullValue() {
        assertThatThrownBy(() -> new MembershipId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membershipId must be positive");
    }

    @Test
    @DisplayName("0은 거부한다")
    void rejectsZeroValue() {
        assertThatThrownBy(() -> new MembershipId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membershipId must be positive");
    }

    @Test
    @DisplayName("음수는 거부한다")
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> new MembershipId(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("membershipId must be positive");
    }
}
