package com.orbit.schedule.application.port.in.command.dto;

import java.time.Instant;
import java.util.List;

/**
 * 배정·재배정·일정 변경 결과. 같은 기사의 활성 작업과 일정이 겹치는데 확인하지 않은 요청이면 반영하지 않고(applied=false) 겹친 작업을 시작시각 순으로 담는다.
 * 반영했으면 applied=true이며, 확인하고 겹침을 허용했다면 겹친 작업도 함께 담는다. 반영하지 않은 결과에는 겹친 작업이 반드시 있다.
 */
public record ScheduleChangeResult(boolean applied, List<ConflictingWork> conflicts) {

    public ScheduleChangeResult {
        conflicts = List.copyOf(conflicts);
        if (!applied && conflicts.isEmpty()) {
            throw new IllegalArgumentException("withheld result must have conflicts");
        }
    }

    public static ScheduleChangeResult applied(List<ConflictingWork> conflicts) {
        return new ScheduleChangeResult(true, conflicts);
    }

    public static ScheduleChangeResult withheld(List<ConflictingWork> conflicts) {
        return new ScheduleChangeResult(false, conflicts);
    }

    /** 겹친 작업의 식별자·작업명·일정. */
    public record ConflictingWork(Long workId, String name, Instant startTime, Instant endTime) {}
}
