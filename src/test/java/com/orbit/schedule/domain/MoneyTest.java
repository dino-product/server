package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("금액")
class MoneyTest {

    @Test
    @DisplayName("원 단위 금액을 생성한다")
    void createsWonAmount() {
        assertThat(new Money(150_000L).won()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("0원을 허용한다")
    void allowsZero() {
        assertThat(new Money(0L).won()).isZero();
    }

    @Test
    @DisplayName("음수 금액을 거부한다")
    void rejectsNegative() {
        assertThatThrownBy(() -> new Money(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("won must not be negative");
    }

    @Test
    @DisplayName("같은 금액은 동등하다")
    void hasValueEquality() {
        assertThat(new Money(150_000L)).isEqualTo(new Money(150_000L)).hasSameHashCodeAs(new Money(150_000L));
    }
}
