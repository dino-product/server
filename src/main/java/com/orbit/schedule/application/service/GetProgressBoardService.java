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
 * 관리자 진행 보드 조회. 구간과 일정이 겹치는 작업(수락대기·수락됨·작업중·완료)을 지금 상태 그대로 열로 나누고(별도의 '이동중' 상태는 두지 않는다) 전체·완료·작업중·
 * 지연 수를 센다. 지연은 지금 시각으로 판정한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetProgressBoardService implements GetProgressBoardUseCase {

    /** 보드의 열 순서. 진행 순서와 같다. */
    private static final List<WorkStatus> COLUMN_ORDER =
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
        return new ProgressBoardInfo(columns, summary(columns));
    }

    /** 열에 담긴 카드로 센다. 전체 수가 열 카드 수의 합과 늘 같다. */
    private static ProgressBoardInfo.Summary summary(List<ProgressBoardInfo.Column> columns) {
        List<ProgressBoardInfo.Card> cards =
                columns.stream().flatMap(column -> column.cards().stream()).toList();
        return new ProgressBoardInfo.Summary(
                cards.size(),
                cardCount(columns, WorkStatus.COMPLETED),
                cardCount(columns, WorkStatus.IN_PROGRESS),
                (int) cards.stream().filter(ProgressBoardInfo.Card::delayed).count());
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

    private static int cardCount(List<ProgressBoardInfo.Column> columns, WorkStatus status) {
        return columns.stream()
                .filter(column -> column.status() == status)
                .mapToInt(column -> column.cards().size())
                .sum();
    }
}
