package com.orbit.schedule.application.service;

import java.util.Comparator;
import java.util.List;

import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult.ConflictingWork;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;

/**
 * 배정·재배정·일정 변경의 공통 마무리. 도메인 변경을 마친 작업의 새 일정을 같은 조직·같은 기사의 활성 작업과 비교하고, 겹치는데 확인하지 않은 요청이면 저장하지 않고 겹친
 * 작업을 시작시각(같으면 식별자) 순으로 돌려준다. 저장하지 않은 변경이 반영되지 않는다는 {@link WorkRepository} 계약에 기댄다.
 */
final class ScheduleChanges {

    private ScheduleChanges() {}

    static ScheduleChangeResult saveUnlessUnconfirmedConflict(
            WorkRepository workRepository, Work changedWork, boolean conflictConfirmed) {
        WorkSchedule schedule = changedWork
                .schedule()
                .orElseThrow(() -> new IllegalStateException("changed work must have a schedule"));
        List<Work> candidates =
                workRepository.findActiveByTechnician(changedWork.organizationId(), schedule.technicianId());
        List<ConflictingWork> conflicts =
                WorkScheduleConflictPolicy.findConflictingWorks(schedule, changedWork, candidates).stream()
                        .map(ScheduleChanges::toConflictingWork)
                        .sorted(Comparator.comparing(ConflictingWork::startTime).thenComparing(ConflictingWork::workId))
                        .toList();
        if (!conflicts.isEmpty() && !conflictConfirmed) {
            return ScheduleChangeResult.withheld(conflicts);
        }

        workRepository.save(changedWork);
        return ScheduleChangeResult.applied(conflicts);
    }

    private static ConflictingWork toConflictingWork(Work work) {
        WorkSchedule schedule =
                work.schedule().orElseThrow(() -> new IllegalStateException("conflicting work must have a schedule"));
        Long id = work.id()
                .orElseThrow(() -> new IllegalStateException("stored work must have an id"))
                .value();
        return new ConflictingWork(id, work.name(), schedule.startTime(), schedule.endTime());
    }
}
