package com.orbit.schedule.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** 작업의 결제 정보(선택 항목). 금액과 결제수단은 서로 독립적으로 선택 가능하다. */
public final class PaymentInfo {

    private final BigDecimal fee;
    private final PaymentMethod method;

    public PaymentInfo(BigDecimal fee, PaymentMethod method) {
        if (fee != null && fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("fee must not be negative");
        }
        this.fee = fee;
        this.method = method;
    }

    public Optional<BigDecimal> fee() {
        return Optional.ofNullable(fee);
    }

    public Optional<PaymentMethod> method() {
        return Optional.ofNullable(method);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PaymentInfo that = (PaymentInfo) o;
        return feeEquals(fee, that.fee) && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fee == null ? null : fee.stripTrailingZeros(), method);
    }

    // BigDecimal.equals는 scale까지 비교해 150000과 150000.00을 다르다고 판단하므로,
    // 생성자 검증과 같은 compareTo 기준(값 동등성)으로 맞춘다.
    private static boolean feeEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    @Override
    public String toString() {
        return "PaymentInfo[fee=" + fee + ", method=" + method + "]";
    }
}
