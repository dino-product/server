package com.orbit.schedule.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetTeamScheduleUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTeamScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.TeamScheduleInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadTechnicianNamePort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/**
 * 기사의 팀 일정 조회. 구간과 일정이 겹치는 조직의 작업(수락대기·수락됨·작업중·완료)을 기사별로 보여 준다. 다른 기사의 작업은 시간·기사 이름·상태만 담고 작업
 * 식별자·작업명·고객 정보는 담지 않는다. 본인 작업은 작업 식별자·작업명도 담아 상세로 이어 갈 수 있게 한다. 관리자는 타임테이블을 쓰므로 요청할 수 없다(403
 * SCHEDULE-005). 오류 확인 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetTeamScheduleService implements GetTeamScheduleUseCase {

    // 다른 기사 작업의 식별자는 응답에 없으므로, 같은 기사·같은 시작시각의 칸은 끝 시각으로만 순서를 정한다(식별자 순서가 드러나지 않게).
    private static final Comparator<TeamScheduleInfo.Slot> SLOT_ORDER = Comparator.comparing(
                    TeamScheduleInfo.Slot::technicianId)
            .thenComparing(TeamScheduleInfo.Slot::startTime)
            .thenComparing(TeamScheduleInfo.Slot::endTime);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianNamePort loadTechnicianNamePort;

    public GetTeamScheduleService(
            LoadActorPort loadActorPort, WorkQueryPort workQueryPort, LoadTechnicianNamePort loadTechnicianNamePort) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.loadTechnicianNamePort = loadTechnicianNamePort;
    }

    @Override
    @Transactional(readOnly = true)
    public TeamScheduleInfo get(GetTeamScheduleQuery query) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());

        List<Work> works = workQueryPort.listScheduledBetween(technician.organizationId(), period.from(), period.to());
        Set<TechnicianId> technicianIds = works.stream()
                .map(work -> work.schedule().orElseThrow().technicianId())
                .collect(Collectors.toSet());
        Map<TechnicianId, String> names = technicianIds.isEmpty()
                ? Map.of()
                : loadTechnicianNamePort.loadNames(technician.organizationId(), technicianIds);
        return new TeamScheduleInfo(works.stream()
                .map(work -> slot(work, technician, names))
                .sorted(SLOT_ORDER)
                .toList());
    }

    private static TeamScheduleInfo.Slot slot(Work work, TechnicianActor technician, Map<TechnicianId, String> names) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        boolean mine = schedule.technicianId().equals(technician.technicianId());
        return new TeamScheduleInfo.Slot(
                schedule.technicianId().value(),
                names.get(schedule.technicianId()),
                schedule.startTime(),
                schedule.endTime(),
                work.status(),
                mine,
                mine ? work.id().orElseThrow().value() : null,
                mine ? work.name() : null);
    }
}
