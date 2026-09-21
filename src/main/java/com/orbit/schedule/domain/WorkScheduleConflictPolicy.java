package com.orbit.schedule.domain;

import java.util.List;

/**
 * 같은 기사의 일정 겹침·동시 진행 여부를 판정하는 순수 정책. 리포지토리 조회는 이 정책의 범위 밖이며 호출자가 배선한다.
 * 두 메서드의 기사 스코프 책임은 다르다.
 * {@link #overlaps}는 candidate가 technicianId를 갖고 있어 호출자가 같은 기사로 좁혀 전달한다고 가정하지
 * 않고 정책이 스스로 다시 걸러낸다. 반면 {@link #hasConcurrentInProgress}는 비교 기준이 될 기사를 받지
 * 않으므로 자체적으로 기사를 좁힐 수 없다 — 전달받은 목록이 이미 대상 기사 것으로 좁혀져 있다고 가정하며,
 * 그 안에서 IN_PROGRESS 상태만 방어적으로 다시 걸러낸다.
 */
public final class WorkScheduleConflictPolicy {

    private WorkScheduleConflictPolicy() {}

    public static boolean overlaps(WorkSchedule candidate, List<WorkSchedule> existingSchedules) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (existingSchedules == null) {
            throw new IllegalArgumentException("existingSchedules must not be null");
        }
        return existingSchedules.stream()
                .filter(existing -> existing.technicianId().equals(candidate.technicianId()))
                .anyMatch(existing -> timeRangesOverlap(candidate, existing));
    }

    // [start, end) 반열림 구간 비교: 한쪽 시작시각이 다른 쪽 종료시각과 같은 인접 상태는 겹침이 아니다.
    private static boolean timeRangesOverlap(WorkSchedule a, WorkSchedule b) {
        return a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
    }

    public static boolean hasConcurrentInProgress(List<Work> inProgressWorks) {
        if (inProgressWorks == null) {
            throw new IllegalArgumentException("inProgressWorks must not be null");
        }
        return inProgressWorks.stream().anyMatch(work -> work.status() == WorkStatus.IN_PROGRESS);
    }
}
