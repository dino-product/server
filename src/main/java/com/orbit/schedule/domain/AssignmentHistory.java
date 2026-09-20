package com.orbit.schedule.domain;

import java.util.Optional;

/** Work에 속한 하나의 배정 시도와 그 결과. */
public final class AssignmentHistory {

    private final WorkSchedule schedule;
    private AssignmentResult result;
    private RejectionReason rejectionReason;

    public AssignmentHistory(WorkSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        this.schedule = schedule;
        this.result = AssignmentResult.PENDING;
    }

    public void accept() {
        requirePending("accept");
        result = AssignmentResult.ACCEPTED;
    }

    public void reject(RejectionReason reason) {
        requirePending("reject");
        if (reason == null) {
            throw new IllegalArgumentException("rejectionReason must not be null");
        }
        result = AssignmentResult.REJECTED;
        rejectionReason = reason;
    }

    public void reassign() {
        requirePending("reassign");
        result = AssignmentResult.REASSIGNED;
    }

    public WorkSchedule schedule() {
        return schedule;
    }

    public AssignmentResult result() {
        return result;
    }

    public Optional<RejectionReason> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    private void requirePending(String action) {
        if (result != AssignmentResult.PENDING) {
            throw new IllegalStateException("Cannot " + action + " when result is " + result);
        }
    }
}
