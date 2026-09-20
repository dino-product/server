package com.orbit.schedule.domain;

import java.math.BigDecimal;
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
}
