package com.orbit.schedule.domain;

/**
 * 작업 진행 상태와 전이 규칙. 대기함은 기사·시간 미배정을 나타내는 파생 조회 상태라 여기 포함하지 않는다. 거절은 상태가 아니라 배정 이력의 결과로만 남고 작업은
 * 대기함(REGISTERED)으로 돌아간다. 수락 이후 재배정·일정 변경은 다시 수락대기로 돌아간다. 수락대기 중의 재배정·일정 변경처럼 상태가
 * 그대로인 변경은 전이가 아니므로, 자기 자신으로의 전이는 허용하지 않고 호출자도 전이를 요청하지 않는다.
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

    /** 완료·취소처럼 어떤 상태로도 전이할 수 없는 종료 상태인지. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public WorkStatus transitionTo(WorkStatus nextStatus) {
        if (!canTransitionTo(nextStatus)) {
            throw new IllegalStateException("Cannot transition from " + this + " to " + nextStatus);
        }
        return nextStatus;
    }
}
