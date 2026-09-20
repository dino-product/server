package com.orbit.schedule.domain;

public record WorkId(Long value) {

    public WorkId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("workId must be positive");
        }
    }
}
