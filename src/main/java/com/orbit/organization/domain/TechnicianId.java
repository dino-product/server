package com.orbit.organization.domain;

public record TechnicianId(Long value) {

    public TechnicianId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("technicianId must be positive");
        }
    }
}
