package com.orbit.schedule.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Work에 속한 하나의 배정 시도와 그 결과. 배정한 사람과 시각, 기사의 응답(수락·거절) 시각, 관리자 조치로 배정이 끝난 기록을 남긴다. 수락·거절로 확정된 결과는
 * 바꾸지 않으며, 재배정 시에는 새 이력을 추가한다. 배정·응답 시각은 저장소 정밀도인 마이크로초로 잘라 둔다.
 *
 * <ul>
 *   <li>응답 전에 끝나면 결과는 {@link AssignmentResult#WITHDRAWN}(응답 전 회수)이고 응답 시각은 종료 시각과 같다.
 *   <li>수락된 뒤에 끝나면 결과는 수락 그대로 두고 종료 기록만 더한다.
 *   <li>거절은 기사의 응답으로 끝난 것이라 종료 기록이 없다.
 * </ul>
 */
public final class AssignmentHistory {

    private final WorkSchedule schedule;
    private final Instant assignedAt;
    private final MembershipId assignedBy;
    private AssignmentResult result;
    private Rejection rejection;
    private Instant decidedAt;
    private AssignmentEnding ending;

    public AssignmentHistory(WorkSchedule schedule, Instant assignedAt, MembershipId assignedBy) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        if (assignedAt == null) {
            throw new IllegalArgumentException("assignedAt must not be null");
        }
        if (assignedBy == null) {
            throw new IllegalArgumentException("assignedBy must not be null");
        }
        this.schedule = schedule;
        this.assignedAt = assignedAt.truncatedTo(ChronoUnit.MICROS);
        this.assignedBy = assignedBy;
        this.result = AssignmentResult.PENDING;
    }

    /** 저장된 이력을 복원한다. 결과와 응답 시각·거절 사유·종료 기록의 정합성을 검증한다. */
    public static AssignmentHistory restore(
            WorkSchedule schedule,
            Instant assignedAt,
            MembershipId assignedBy,
            AssignmentResult result,
            Rejection rejection,
            Instant decidedAt,
            AssignmentEnding ending) {
        AssignmentHistory history = new AssignmentHistory(schedule, assignedAt, assignedBy);
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (result != AssignmentResult.REJECTED && rejection != null) {
            throw new IllegalArgumentException("Only REJECTED history can have a rejection");
        }
        if (result == AssignmentResult.PENDING) {
            if (decidedAt != null) {
                throw new IllegalArgumentException("PENDING history must not have decidedAt");
            }
            requireNoEnding(result, ending);
            return history;
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException(result + " history must have decidedAt");
        }
        if (result == AssignmentResult.REJECTED) {
            requireNoEnding(result, ending);
            history.reject(rejection, decidedAt);
            return history;
        }
        if (result == AssignmentResult.ACCEPTED) {
            history.accept(decidedAt);
            if (ending != null) {
                history.end(ending);
            }
            return history;
        }
        if (ending == null) {
            throw new IllegalArgumentException("WITHDRAWN history must have an ending");
        }
        if (!ending.endedAt().equals(decidedAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException("WITHDRAWN history must be decided when it ended");
        }
        history.end(ending);
        return history;
    }

    void accept(Instant decidedAt) {
        decide(AssignmentResult.ACCEPTED, decidedAt, "accept");
    }

    void reject(Rejection newRejection, Instant decidedAt) {
        requirePending("reject");
        if (newRejection == null) {
            throw new IllegalArgumentException("rejection must not be null");
        }
        decide(AssignmentResult.REJECTED, decidedAt, "reject");
        rejection = newRejection;
    }

    /**
     * 관리자의 재배정·일정 변경·배정 해제·취소로 이 배정을 끝낸다. 응답 전이면 응답 전 회수로 확정하고, 수락된 뒤면 결과는 그대로 둔다. 거절됐거나 이미 끝난
     * 배정은 끝낼 수 없다. 검증을 모두 마친 뒤 반영한다.
     */
    void end(AssignmentEnding newEnding) {
        if (newEnding == null) {
            throw new IllegalArgumentException("ending must not be null");
        }
        if (ending != null) {
            throw new IllegalStateException("Cannot end an assignment that already ended");
        }
        if (result == AssignmentResult.REJECTED) {
            throw new IllegalStateException("Cannot end when result is REJECTED");
        }
        Instant lowerBound = decidedAt == null ? assignedAt : decidedAt;
        if (newEnding.endedAt().isBefore(lowerBound)) {
            throw new IllegalArgumentException("endedAt must not be before the assignment was assigned or decided");
        }
        if (result == AssignmentResult.PENDING) {
            decide(AssignmentResult.WITHDRAWN, newEnding.endedAt(), "end");
        }
        ending = newEnding;
    }

    public WorkSchedule schedule() {
        return schedule;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public MembershipId assignedBy() {
        return assignedBy;
    }

    public AssignmentResult result() {
        return result;
    }

    public Optional<Rejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    public Optional<RejectionReason> rejectionReason() {
        return rejection().map(Rejection::reason);
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Optional<AssignmentEnding> ending() {
        return Optional.ofNullable(ending);
    }

    /** 더는 현재 배정이 아닌지. 기사가 거절했거나 관리자 조치로 끝났다. 완료된 작업의 마지막 배정은 끝난 것이 아니다. */
    boolean isOver() {
        return result == AssignmentResult.REJECTED || ending != null;
    }

    /** 이 이력에 기록된 마지막 시각. 다음 배정은 이보다 앞설 수 없다. */
    Instant lastMoment() {
        if (ending != null) {
            return ending.endedAt();
        }
        return decidedAt == null ? assignedAt : decidedAt;
    }

    private void decide(AssignmentResult newResult, Instant newDecidedAt, String action) {
        requirePending(action);
        if (newDecidedAt == null) {
            throw new IllegalArgumentException("decidedAt must not be null");
        }
        Instant storedDecidedAt = newDecidedAt.truncatedTo(ChronoUnit.MICROS);
        if (storedDecidedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("decidedAt must not be before assignedAt");
        }
        result = newResult;
        decidedAt = storedDecidedAt;
    }

    private void requirePending(String action) {
        if (result != AssignmentResult.PENDING) {
            throw new IllegalStateException("Cannot " + action + " when result is " + result);
        }
    }

    private static void requireNoEnding(AssignmentResult result, AssignmentEnding ending) {
        if (ending != null) {
            throw new IllegalArgumentException(result + " history must not have an ending");
        }
    }
}
