package com.orbit.schedule.domain;

import java.util.List;
import java.util.Set;

/**
 * 같은 기사의 일정 겹침·동시 수행을 판정하는 순수 정책. 리포지토리 조회는 이 정책의 범위 밖이며 호출자가 후보 작업을 넘긴다. 기사·상태·자기 자신은 호출자가 목록을
 * 좁혔는지와 관계없이 정책이 스스로 다시 걸러낸다.
 */
public final class WorkScheduleConflictPolicy {

    private static final Set<WorkStatus> ACTIVE_STATUSES =
            Set.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS);

    private WorkScheduleConflictPolicy() {}

    /**
     * candidate와 같은 기사의 활성 작업(수락대기·수락됨·작업중) 중 시간이 겹치는 작업을 목록 순서대로 반환한다. 일정을 바꾸는 작업 자신(target)은 같은 인스턴스이거나
     * 같은 식별자면 제외한다. 새 작업처럼 자신이 없으면 target은 null이다.
     */
    public static List<Work> findConflictingWorks(WorkSchedule candidate, Work target, List<Work> existingWorks) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (existingWorks == null) {
            throw new IllegalArgumentException("existingWorks must not be null");
        }
        return existingWorks.stream()
                .filter(existing -> !isSameWork(existing, target))
                .filter(existing -> ACTIVE_STATUSES.contains(existing.status()))
                .filter(existing -> existing.schedule()
                        .filter(schedule -> schedule.technicianId().equals(candidate.technicianId()))
                        .filter(schedule -> timeRangesOverlap(candidate, schedule))
                        .isPresent())
                .toList();
    }

    /** 해당 기사에게 이미 작업중인 작업이 있는지 판정한다. */
    public static boolean hasConcurrentInProgress(MembershipId technicianId, List<Work> works) {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
        if (works == null) {
            throw new IllegalArgumentException("works must not be null");
        }
        return works.stream()
                .filter(work -> work.status() == WorkStatus.IN_PROGRESS)
                .anyMatch(work -> work.schedule()
                        .map(schedule -> schedule.technicianId().equals(technicianId))
                        .orElse(false));
    }

    // [start, end) 반열림 구간 비교: 한쪽 시작시각이 다른 쪽 종료시각과 같은 인접 상태는 겹침이 아니다.
    private static boolean timeRangesOverlap(WorkSchedule a, WorkSchedule b) {
        return a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
    }

    private static boolean isSameWork(Work existing, Work target) {
        if (target == null) {
            return false;
        }
        return existing == target || (existing.id().isPresent() && existing.id().equals(target.id()));
    }
}
