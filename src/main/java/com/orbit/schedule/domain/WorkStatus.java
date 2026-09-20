package com.orbit.schedule.domain;

/** 작업 진행 상태와 정방향 전이 규칙. 대기함은 기사·시간 미배정을 나타내는 파생 조회 상태라 여기 포함하지 않는다. */
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
            case REJECTED -> nextStatus == REGISTERED;
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
