package com.orbit.organization.domain;

/** auth 모듈이 발급한 계정을 opaque ID로만 참조하는 값객체. 계정은 역할 없이 존재하고 발주사별 소속이 역할을 가진다. */
public record AccountId(Long value) {

    public AccountId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
    }
}
