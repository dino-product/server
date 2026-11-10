package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshot;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.ShownSchedule;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;

/**
 * 진행 보드 스냅샷을 DB에서 만든다. 보일 일정이 구간과 겹치는 작업과 카드에 보일 담당 기사의 표시 정보를 한 스냅샷(REPEATABLE READ)에서 읽는다. 요청자 확인과
 * 구간 검증은 {@link GetProgressBoardService}가 먼저 한다. 그 서비스는 트랜잭션을 열지 않으므로 이 트랜잭션이 따로 시작된다.
 */
@Component
public class ProgressBoardSnapshotReader {

    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;

    public ProgressBoardSnapshotReader(
            WorkQueryPort workQueryPort, LoadTechnicianProfilesPort loadTechnicianProfilesPort) {
        this.workQueryPort = workQueryPort;
        this.loadTechnicianProfilesPort = loadTechnicianProfilesPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProgressBoardSnapshot read(OrganizationId organizationId, Instant from, Instant to) {
        List<Work> works = workQueryPort.listShownBetween(organizationId, from, to);
        DisplayInfos displayInfos = DisplayInfos.ofAssignees(loadTechnicianProfilesPort, organizationId, works);
        return new ProgressBoardSnapshot(
                works.stream().map(work -> card(work, displayInfos)).toList());
    }

    private static ProgressBoardSnapshot.Card card(Work work, DisplayInfos displayInfos) {
        ShownSchedule shown = work.shownSchedule().orElseThrow();
        TechnicianId technicianId = shown.technicianId();
        return new ProgressBoardSnapshot.Card(
                work.id().orElseThrow().value(),
                work.status(),
                work.name(),
                technicianId == null ? null : displayInfos.technician(technicianId),
                shown.startTime(),
                shown.endTime().orElse(null),
                work.customerInfo().name().orElse(null),
                work.isReturnedByRejection(),
                work.delayThreshold().orElse(null));
    }
}
