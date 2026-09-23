package com.orbit.schedule.domain;

/**
 * 작업 진행 상태와 전이 규칙. 대기함은 기사·시간 미배정을 나타내는 파생 조회 상태라 여기 포함하지 않는다. 거절은 상태가 아니라 배정 이력의 결과로만 남고 작업은
 * 대기함(REGISTERED)으로 돌아간다. 수락 이후 재배정·일정 변경은 다시 수락대기로 돌아간다.
 */
public enum WorkStatus {
    REGISTERED,
    PENDING_ACCEPTANCE,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(WorkStatus nextStatus) {
        return switch (this) {
            case REGISTERED -> nextStatus == PENDING_ACCEPTANCE || nextStatus == CANCELLED;
            case PENDING_ACCEPTANCE -> nextStatus == ACCEPTED || nextStatus == REGISTERED || nextStatus == CANCELLED;
            case ACCEPTED ->
                nextStatus == IN_PROGRESS
                        || nextStatus == PENDING_ACCEPTANCE
                        || nextStatus == REGISTERED
                        || nextStatus == CANCELLED;
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
