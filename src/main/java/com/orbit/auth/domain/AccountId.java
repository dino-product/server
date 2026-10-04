package com.orbit.auth.domain;

/** auth가 발급하는 계정 식별자. 다른 모듈은 이 값을 불투명 참조로만 사용한다. */
public record AccountId(Long value) {

    public AccountId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
    }
}
