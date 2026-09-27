package com.orbit.schedule.domain;

/** 원 단위 금액. 원화에는 소수 단위가 없으므로 정수로만 다루며 음수는 허용하지 않는다. */
public record Money(long won) {

    public Money {
        if (won < 0) {
            throw new IllegalArgumentException("won must not be negative");
        }
    }
}
