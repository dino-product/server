package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.Comparator;

import com.orbit.schedule.application.port.in.query.dto.BacklogWorkInfo;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.Work;

/** 대기함 작업을 카드로 옮기는 변환. 타임테이블의 대기함과 작업 목록의 대기함 요약이 같은 카드를 쓴다. */
final class BacklogWorks {

    /** 대기함의 표시 순서: 오래 등록된 작업 먼저, 같으면 작업 식별자 순. */
    static final Comparator<Work> ORDER = Comparator.comparing(Work::registeredAt)
            .thenComparing(work -> work.id().orElseThrow().value());

    private BacklogWorks() {}

    static BacklogWorkInfo card(Work work, Instant now, DisplayInfos displayInfos) {
        PlannedSchedule planned = work.plannedSchedule().orElse(null);
        Rejection rejection = work.isReturnedByRejection()
                ? work.assignmentHistory().getLast().rejection().orElseThrow()
                : null;
        return new BacklogWorkInfo(
                work.id().orElseThrow().value(),
                work.name(),
                displayInfos.workType(work),
                displayInfos.registrar(work),
                work.registeredAt(),
                displayInfos.plannedTechnician(planned),
                planned == null ? null : planned.startTime(),
                planned == null ? null : planned.expectedDuration(),
                planned == null || planned.technicianId() == null,
                planned == null || planned.startTime() == null || planned.expectedDuration() == null,
                rejection != null,
                rejection == null ? null : rejection.reason(),
                rejection == null ? null : rejection.note(),
                work.isStaleInBacklogAt(now));
    }
}
