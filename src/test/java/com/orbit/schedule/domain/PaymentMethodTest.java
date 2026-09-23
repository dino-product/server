package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제수단")
class PaymentMethodTest {

    @Test
    @DisplayName("결제수단 3개가 정의된 순서대로 존재한다")
    void containsAllMethodsInOrder() {
        assertThat(PaymentMethod.values())
                .containsExactly(PaymentMethod.ON_SITE_CARD, PaymentMethod.ON_SITE_CASH, PaymentMethod.BANK_TRANSFER);
    }
}
