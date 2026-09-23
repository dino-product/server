package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제 정보")
class PaymentInfoTest {

    @Test
    @DisplayName("같은 결제 정보는 동등하다")
    void hasValueEquality() {
        PaymentInfo paymentInfo = new PaymentInfo(new BigDecimal("150000"), PaymentMethod.BANK_TRANSFER);
        PaymentInfo samePaymentInfo = new PaymentInfo(new BigDecimal("150000"), PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo).isEqualTo(samePaymentInfo);
        assertThat(paymentInfo.hashCode()).isEqualTo(samePaymentInfo.hashCode());
    }

    @Test
    @DisplayName("하나라도 다른 결제 정보는 동등하지 않다")
    void isNotEqualWhenAnyValueDiffers() {
        PaymentInfo paymentInfo = new PaymentInfo(new BigDecimal("150000"), PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo)
                .isNotEqualTo(new PaymentInfo(new BigDecimal("200000"), PaymentMethod.BANK_TRANSFER))
                .isNotEqualTo(new PaymentInfo(new BigDecimal("150000"), PaymentMethod.ON_SITE_CARD));
    }

    @Test
    @DisplayName("작업 금액만 포함해 생성할 수 있다")
    void createsWithFeeOnly() {
        PaymentInfo paymentInfo = new PaymentInfo(BigDecimal.ZERO, null);

        assertThat(paymentInfo.fee()).contains(BigDecimal.ZERO);
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
        BigDecimal fee = new BigDecimal("150000");

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

    @Test
    @DisplayName("금액의 scale이 달라도 값이 같으면 동등하다")
    void isEqualWhenFeeScaleDiffers() {
        PaymentInfo paymentInfo = new PaymentInfo(new BigDecimal("150000"), PaymentMethod.BANK_TRANSFER);
        PaymentInfo sameValueDifferentScale = new PaymentInfo(new BigDecimal("150000.00"), PaymentMethod.BANK_TRANSFER);

        assertThat(paymentInfo).isEqualTo(sameValueDifferentScale);
        assertThat(paymentInfo.hashCode()).isEqualTo(sameValueDifferentScale.hashCode());
    }

    @Test
    @DisplayName("작업 금액이 음수이면 거부한다")
    void rejectsNegativeFee() {
        assertThatThrownBy(() -> new PaymentInfo(new BigDecimal("-0.01"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fee must not be negative");
    }
}
