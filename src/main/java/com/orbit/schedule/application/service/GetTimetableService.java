package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.GetTimetableUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkScheduleConflictPolicy;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

/**
 * 관리자 타임테이블 조회. 활성 기사 행, 구간과 일정이 겹치는 기사별 작업(수락대기·수락됨·작업중·완료), 대기함 작업을 함께 돌려준다. 지연·대기함 방치는 지금
 * 시각으로, 겹침은 같은 기사의 활성 작업끼리 일정 겹침 정책으로 표시한다. 겹치는 짝이 구간 밖에 있어도 표시하도록, 예상소요시간 상한만큼 넓힌 구간의 작업을 겹침
 * 후보로 읽는다. 기사유형·작업유형 조건은 기사 행과 작업 칸에만 걸고 겹침 판정과 대기함에는 걸지 않는다. 칸과 대기함을 같은 시점의 상태로 보이도록 한
 * 스냅샷(REPEATABLE READ)에서 읽는다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인 순서는 요청자 → 구간 → 유형 조건이다.
 */
@Service
public class GetTimetableService implements GetTimetableUseCase {

    private static final Comparator<TimetableInfo.ScheduledWork> SCHEDULED_ORDER = Comparator.comparing(
                    (TimetableInfo.ScheduledWork work) -> work.technician().technicianId())
            .thenComparing(TimetableInfo.ScheduledWork::startTime)
            .thenComparing(TimetableInfo.ScheduledWork::workId);

    private static final Comparator<TechnicianInfo> ROW_ORDER = Comparator.comparing(
                    TechnicianInfo::name, Comparator.nullsLast(Comparator.<String>naturalOrder()))
            .thenComparing(TechnicianInfo::technicianId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final LoadStaffProfilesPort loadStaffProfilesPort;
    private final LoadWorkTypeLabelsPort loadWorkTypeLabelsPort;
    private final Clock clock;

    public GetTimetableService(
            LoadActorPort loadActorPort,
            WorkQueryPort workQueryPort,
            LoadTechnicianProfilesPort loadTechnicianProfilesPort,
            LoadStaffProfilesPort loadStaffProfilesPort,
            LoadWorkTypeLabelsPort loadWorkTypeLabelsPort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.loadTechnicianProfilesPort = loadTechnicianProfilesPort;
        this.loadStaffProfilesPort = loadStaffProfilesPort;
        this.loadWorkTypeLabelsPort = loadWorkTypeLabelsPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TimetableInfo get(GetTimetableQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Set<Long> technicianTypeIds = typeIds(query.technicianTypeIds());
        Set<Long> workTypeIds = typeIds(query.workTypeIds());
        Instant now = clock.instant();

        // 구간 안 작업과 겹칠 수 있는 작업은 시작·종료가 구간에서 예상소요시간 상한 이내에 있다.
        QueryPeriod candidateWindow = period.widenedBy(WorkSchedule.MAX_EXPECTED_DURATION);
        List<Work> candidates = workQueryPort.listScheduledBetween(
                manager.organizationId(), candidateWindow.from(), candidateWindow.to());
        Map<TechnicianId, List<Work>> activeByTechnician = candidates.stream()
                .filter(work -> work.status().isActive())
                .collect(Collectors.groupingBy(
                        work -> work.schedule().orElseThrow().technicianId()));
        List<Work> inPeriod = candidates.stream()
                .filter(work -> period.overlaps(work.schedule().orElseThrow()))
                .toList();
        List<Work> backlog = workQueryPort.listBacklog(manager.organizationId()).stream()
                .sorted(BacklogWorks.ORDER)
                .toList();
        DisplayInfos displayInfos = DisplayInfos.of(
                loadTechnicianProfilesPort,
                loadStaffProfilesPort,
                loadWorkTypeLabelsPort,
                manager.organizationId(),
                Stream.concat(inPeriod.stream(), backlog.stream()).toList());

        List<TimetableInfo.ScheduledWork> scheduled = inPeriod.stream()
                .filter(work -> hasTechnicianType(work, technicianTypeIds, displayInfos))
                .filter(work -> hasWorkType(work, workTypeIds, displayInfos))
                .map(work -> scheduledWork(work, now, activeByTechnician, displayInfos))
                .sorted(SCHEDULED_ORDER)
                .toList();
        List<TechnicianInfo> technicians = loadTechnicianProfilesPort.listActive(manager.organizationId()).stream()
                .filter(profile -> technicianTypeIds.isEmpty() || isOfType(profile.technicianType(), technicianTypeIds))
                .map(DisplayInfos::technicianInfo)
                .sorted(ROW_ORDER)
                .toList();
        return new TimetableInfo(
                technicians,
                scheduled,
                backlog.stream()
                        .map(work -> BacklogWorks.card(work, now, displayInfos))
                        .toList());
    }

    /** 유형 조건. 비어 있으면 거르지 않는다. 식별자는 양수여야 한다(아니면 400 SCHEDULE-003). */
    private static Set<Long> typeIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        return Set.copyOf(ids);
    }

    private static boolean hasTechnicianType(Work work, Set<Long> technicianTypeIds, DisplayInfos displayInfos) {
        return technicianTypeIds.isEmpty()
                || isOfType(
                        displayInfos
                                .technicianType(work.schedule().orElseThrow().technicianId())
                                .orElse(null),
                        technicianTypeIds);
    }

    private static boolean hasWorkType(Work work, Set<Long> workTypeIds, DisplayInfos displayInfos) {
        return workTypeIds.isEmpty()
                || displayInfos
                        .shownWorkType(work)
                        .map(WorkTypeId::value)
                        .filter(workTypeIds::contains)
                        .isPresent();
    }

    private static boolean isOfType(TypeLabel type, Set<Long> typeIds) {
        return type != null && typeIds.contains(type.id());
    }

    private static TimetableInfo.ScheduledWork scheduledWork(
            Work work, Instant now, Map<TechnicianId, List<Work>> activeByTechnician, DisplayInfos displayInfos) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        boolean conflicting = work.status().isActive()
                && !WorkScheduleConflictPolicy.findConflictingWorks(
                                schedule, work, activeByTechnician.getOrDefault(schedule.technicianId(), List.of()))
                        .isEmpty();
        return new TimetableInfo.ScheduledWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                displayInfos.assignee(schedule),
                schedule.startTime(),
                schedule.endTime(),
                work.isDelayedAt(now),
                conflicting,
                displayInfos.workType(work),
                displayInfos.registrar(work),
                work.version());
    }
}
