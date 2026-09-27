package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 관리자 진행 보드 조회. 구간과 일정이 겹치는 작업(수락대기·수락됨·작업중·완료)을 지금 상태 그대로 열로 나누고(별도의 '이동중' 상태는 두지 않는다) 완료·작업중·지연
 * 수를 센다. 지연은 지금 시각으로 판정한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetProgressBoardService implements GetProgressBoardUseCase {

    /** 보드의 열 순서. 진행 순서와 같다. */
    static final List<WorkStatus> COLUMN_ORDER =
            List.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS, WorkStatus.COMPLETED);

    private static final Comparator<ProgressBoardInfo.Card> CARD_ORDER =
            Comparator.comparing(ProgressBoardInfo.Card::startTime).thenComparing(ProgressBoardInfo.Card::workId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final Clock clock;

    public GetProgressBoardService(LoadActorPort loadActorPort, WorkQueryPort workQueryPort, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressBoardInfo get(GetProgressBoardQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Instant now = clock.instant();

        List<Work> works = workQueryPort.listScheduledBetween(manager.organizationId(), period.from(), period.to());
        List<ProgressBoardInfo.Column> columns = COLUMN_ORDER.stream()
                .map(status -> new ProgressBoardInfo.Column(
                        status,
                        works.stream()
                                .filter(work -> work.status() == status)
                                .map(work -> card(work, now))
                                .sorted(CARD_ORDER)
                                .toList()))
                .toList();
        int delayed = (int) works.stream().filter(work -> work.isDelayedAt(now)).count();
        ProgressBoardInfo.Summary summary = new ProgressBoardInfo.Summary(
                works.size(), count(works, WorkStatus.COMPLETED), count(works, WorkStatus.IN_PROGRESS), delayed);
        return new ProgressBoardInfo(columns, summary);
    }

    private static ProgressBoardInfo.Card card(Work work, Instant now) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        return new ProgressBoardInfo.Card(
                work.id().orElseThrow().value(),
                work.name(),
                schedule.technicianId().value(),
                schedule.startTime(),
                schedule.endTime(),
                work.isDelayedAt(now));
    }

    private static int count(List<Work> works, WorkStatus status) {
        return (int) works.stream().filter(work -> work.status() == status).count();
    }
}
