package com.orbit.organization.domain;

/** 작업 유형 식별자. 직원·기사 유형과 저장소가 달라 같은 값이 겹칠 수 있다. */
public record WorkTypeId(Long value) {

    public WorkTypeId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("workTypeId must be positive");
        }
    }
}
