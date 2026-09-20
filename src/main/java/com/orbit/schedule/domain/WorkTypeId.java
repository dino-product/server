package com.orbit.schedule.domain;

/** organization 모듈의 WorkType을 opaque ID로만 참조하는 ACL 값객체. 미지정 가능 여부는 이 타입이 아니라 Work의 Optional 접근자가 책임진다. */
public record WorkTypeId(Long value) {

    public WorkTypeId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("workTypeId must be positive");
        }
    }
}
