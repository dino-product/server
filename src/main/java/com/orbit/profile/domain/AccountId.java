package com.orbit.profile.domain;

/** auth 모듈이 발급한 계정을 불투명 식별자로만 참조하는 값객체. 프로필은 계정마다 하나다. */
public record AccountId(Long value) {

    public AccountId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
    }
}
