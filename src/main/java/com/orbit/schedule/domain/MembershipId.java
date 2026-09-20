package com.orbit.schedule.domain;

public record MembershipId(Long value) {

    public MembershipId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("membershipId must be positive");
        }
    }
}
