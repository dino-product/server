package com.orbit.schedule.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Work에 속한 하나의 배정 시도와 그 결과. 배정 시각과 응답(수락·거절·응답 전 마감) 시각을 남긴다. 수락·거절로 확정된 결과는 바꾸지 않으며, 재배정 시에는 새 이력을
 * 추가한다.
 */
public final class AssignmentHistory {

    private final WorkSchedule schedule;
    private final Instant assignedAt;
    private AssignmentResult result;
    private RejectionReason rejectionReason;
    private Instant decidedAt;

    public AssignmentHistory(WorkSchedule schedule, Instant assignedAt) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        if (assignedAt == null) {
            throw new IllegalArgumentException("assignedAt must not be null");
        }
        this.schedule = schedule;
        this.assignedAt = assignedAt;
        this.result = AssignmentResult.PENDING;
    }

    /** 저장된 이력을 복원한다. 결과와 응답 시각·거절 사유의 정합성을 검증한다. */
    public static AssignmentHistory restore(
            WorkSchedule schedule,
            Instant assignedAt,
            AssignmentResult result,
            RejectionReason rejectionReason,
            Instant decidedAt) {
        AssignmentHistory history = new AssignmentHistory(schedule, assignedAt);
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (result == AssignmentResult.PENDING) {
            if (decidedAt != null) {
                throw new IllegalArgumentException("PENDING history must not have decidedAt");
            }
            if (rejectionReason != null) {
                throw new IllegalArgumentException("Only REJECTED history can have rejectionReason");
            }
            return history;
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException(result + " history must have decidedAt");
        }
        if (result == AssignmentResult.REJECTED) {
            history.reject(rejectionReason, decidedAt);
            return history;
        }
        requireNoRejectionReason(rejectionReason);
        if (result == AssignmentResult.ACCEPTED) {
            history.accept(decidedAt);
        } else {
            history.reassign(decidedAt);
        }
        return history;
    }

    void accept(Instant decidedAt) {
        decide(AssignmentResult.ACCEPTED, decidedAt, "accept");
    }

    void reject(RejectionReason reason, Instant decidedAt) {
        requirePending("reject");
        if (reason == null) {
            throw new IllegalArgumentException("rejectionReason must not be null");
        }
        decide(AssignmentResult.REJECTED, decidedAt, "reject");
        rejectionReason = reason;
    }

    /** 응답 전에 재배정·일정 변경·배정 해제·취소되어 대기 중인 배정을 마감한다. */
    void reassign(Instant decidedAt) {
        decide(AssignmentResult.REASSIGNED, decidedAt, "reassign");
    }

    public WorkSchedule schedule() {
        return schedule;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public AssignmentResult result() {
        return result;
    }

    public Optional<RejectionReason> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    private void decide(AssignmentResult newResult, Instant newDecidedAt, String action) {
        requirePending(action);
        if (newDecidedAt == null) {
            throw new IllegalArgumentException("decidedAt must not be null");
        }
        if (newDecidedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("decidedAt must not be before assignedAt");
        }
        result = newResult;
        decidedAt = newDecidedAt;
    }

    private void requirePending(String action) {
        if (result != AssignmentResult.PENDING) {
            throw new IllegalStateException("Cannot " + action + " when result is " + result);
        }
    }

    private static void requireNoRejectionReason(RejectionReason rejectionReason) {
        if (rejectionReason != null) {
            throw new IllegalArgumentException("Only REJECTED history can have rejectionReason");
        }
    }
}
