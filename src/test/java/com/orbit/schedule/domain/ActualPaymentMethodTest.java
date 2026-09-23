package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("실제 결제수단")
class ActualPaymentMethodTest {

    @Test
    @DisplayName("실제 결제수단 3개가 정의된 순서대로 존재한다")
    void containsAllMethodsInOrder() {
        assertThat(ActualPaymentMethod.values())
                .containsExactly(
                        ActualPaymentMethod.CASH, ActualPaymentMethod.BANK_TRANSFER, ActualPaymentMethod.CREDIT_CARD);
    }
}
