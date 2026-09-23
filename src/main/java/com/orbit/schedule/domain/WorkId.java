package com.orbit.schedule.domain;

/** Work 애그리게잇 자신을 가리키는 식별자. */
public record WorkId(Long value) {

    public WorkId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("workId must be positive");
        }
    }
}
