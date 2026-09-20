package com.orbit.schedule.domain;

public record WorkTypeId(Long value) {

    public WorkTypeId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("workTypeId must be positive");
        }
    }
}
