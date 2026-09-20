package com.orbit.schedule.domain;

public enum WorkStatus {
    REGISTERED,
    PENDING_ACCEPTANCE,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(WorkStatus nextStatus) {
        return switch (this) {
            case REGISTERED -> nextStatus == PENDING_ACCEPTANCE || nextStatus == CANCELLED;
            case PENDING_ACCEPTANCE -> nextStatus == ACCEPTED || nextStatus == REJECTED || nextStatus == CANCELLED;
            case REJECTED -> nextStatus == PENDING_ACCEPTANCE;
            case ACCEPTED -> nextStatus == IN_PROGRESS || nextStatus == CANCELLED;
            case IN_PROGRESS -> nextStatus == COMPLETED || nextStatus == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public WorkStatus transitionTo(WorkStatus nextStatus) {
        if (!canTransitionTo(nextStatus)) {
            throw new IllegalStateException("Cannot transition from " + this + " to " + nextStatus);
        }
        return nextStatus;
    }
}
