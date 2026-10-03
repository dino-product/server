package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("계정 식별자")
class AccountIdTest {

    @Test
    @DisplayName("양수 값을 보관한다")
    void keepsPositiveValue() {
        assertThat(new AccountId(7L).value()).isEqualTo(7L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("양수가 아니면 거부한다")
    void rejectsNonPositiveValue(Long value) {
        assertThatThrownBy(() -> new AccountId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accountId must be positive");
    }
}
