package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제 정보")
class PaymentInfoTest {

    @Test
    @DisplayName("같은 결제 정보는 동등하다")
    void hasValueEquality() {
        PaymentInfo paymentInfo = new PaymentInfo(new Money(150000L), PaymentMethod.BANK_TRANSFER);
        PaymentInfo samePaymentInfo = new PaymentInfo(new Money(150000L), PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo).isEqualTo(samePaymentInfo);
        assertThat(paymentInfo.hashCode()).isEqualTo(samePaymentInfo.hashCode());
    }

    @Test
    @DisplayName("하나라도 다른 결제 정보는 동등하지 않다")
    void isNotEqualWhenAnyValueDiffers() {
        PaymentInfo paymentInfo = new PaymentInfo(new Money(150000L), PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo)
                .isNotEqualTo(new PaymentInfo(new Money(200000L), PaymentMethod.BANK_TRANSFER))
                .isNotEqualTo(new PaymentInfo(new Money(150000L), PaymentMethod.ON_SITE_CARD));
    }

    @Test
    @DisplayName("작업 금액만 포함해 생성할 수 있다")
    void createsWithFeeOnly() {
        PaymentInfo paymentInfo = new PaymentInfo(new Money(0L), null);

        assertThat(paymentInfo.fee()).contains(new Money(0L));
        assertThat(paymentInfo.method()).isEmpty();
    }

    @Test
    @DisplayName("결제 방식만 포함해 생성할 수 있다")
    void createsWithMethodOnly() {
        PaymentInfo paymentInfo = new PaymentInfo(null, PaymentMethod.ON_SITE_CARD);

        assertThat(paymentInfo.fee()).isEmpty();
        assertThat(paymentInfo.method()).contains(PaymentMethod.ON_SITE_CARD);
    }

    @Test
    @DisplayName("작업 금액과 결제 방식을 모두 포함해 생성할 수 있다")
    void createsWithAllValues() {
        Money fee = new Money(150000L);

        PaymentInfo paymentInfo = new PaymentInfo(fee, PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo.fee()).contains(fee);
        assertThat(paymentInfo.method()).contains(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    @DisplayName("작업 금액과 결제 방식이 모두 null이어도 생성할 수 있다")
    void createsWithAllNullValues() {
        PaymentInfo paymentInfo = new PaymentInfo(null, null);

        assertThat(paymentInfo.fee()).isEmpty();
        assertThat(paymentInfo.method()).isEmpty();
    }
}
