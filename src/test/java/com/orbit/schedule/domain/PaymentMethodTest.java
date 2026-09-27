package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제수단")
class PaymentMethodTest {

    @Test
    @DisplayName("계획 결제수단은 실제 결제수단과 빠짐없이 1:1로 대응한다")
    void correspondsOneToOneWithActualPaymentMethod() {
        assertThat(PaymentMethod.ON_SITE_CARD.actualCounterpart()).isEqualTo(ActualPaymentMethod.CREDIT_CARD);
        assertThat(PaymentMethod.ON_SITE_CASH.actualCounterpart()).isEqualTo(ActualPaymentMethod.CASH);
        assertThat(PaymentMethod.BANK_TRANSFER.actualCounterpart()).isEqualTo(ActualPaymentMethod.BANK_TRANSFER);
        assertThat(Arrays.stream(PaymentMethod.values()).map(PaymentMethod::actualCounterpart))
                .containsExactlyInAnyOrder(ActualPaymentMethod.values());
    }
}
