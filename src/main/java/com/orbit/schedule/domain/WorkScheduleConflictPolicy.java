package com.orbit.schedule.domain;

import java.util.List;

/**
 * 같은 기사의 일정 겹침·동시 진행 여부를 판정하는 순수 정책. 리포지토리 조회는 이 정책의 범위 밖이며 호출자가 배선한다.
 * 입력 목록은 호출자가 같은 기사 것으로 좁혀 전달한다고 가정하지 않고, WorkSchedule의 technicianId·Work의
 * IN_PROGRESS 상태로 각 메서드가 방어적으로 다시 걸러낸다.
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
