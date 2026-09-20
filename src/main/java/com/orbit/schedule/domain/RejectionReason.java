package com.orbit.schedule.domain;

public enum RejectionReason {
    SCHEDULE_CONFLICT,
    ALREADY_ASSIGNED,
    LOCATION_TOO_FAR,
    SCOPE_MISMATCH,
    OTHER
}
