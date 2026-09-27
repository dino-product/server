package com.orbit.schedule.domain;

import java.util.Objects;
import java.util.Optional;

/** 작업의 결제 정보(선택 항목). 금액과 결제수단은 서로 독립적으로 선택 가능하다. */
public final class PaymentInfo {

    private final Money fee;
    private final PaymentMethod method;

    public PaymentInfo(Money fee, PaymentMethod method) {
        this.fee = fee;
        this.method = method;
    }

    public Optional<Money> fee() {
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
        return Objects.equals(fee, that.fee) && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fee, method);
    }

    @Override
    public String toString() {
        return "PaymentInfo[fee=" + fee + ", method=" + method + "]";
    }
}
