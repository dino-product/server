package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.ShownSchedule;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 관리자 진행 보드 조회. 보일 일정({@link com.orbit.schedule.domain.Work#shownSchedule})이 구간과 겹치는 작업(등록·배정됨·작업전·작업중·완료)을
 * 지금 상태 그대로 열로 나누고(별도의 '이동중' 상태는 두지 않는다) 전체·작업중·지연·완료 수를 센다. 등록 열은 미리 입력한 시간이나 마지막 배정 시간이 구간에 든
 * 대기함 작업이고, 거절로 돌아온 작업을 카드에 표시한다. 카드에 보일 담당 기사만 표시 정보를 찾는다. 지연은 지금 시각으로 판정한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인
 * 순서는 요청자 → 구간 입력이다.
 */
@Service
public class GetProgressBoardService implements GetProgressBoardUseCase {

    /** 보드의 열 순서. 진행 순서와 같다. */
    private static final List<WorkStatus> COLUMN_ORDER = List.of(
            WorkStatus.REGISTERED,
            WorkStatus.PENDING_ACCEPT,
            WorkStatus.ACCEPTED,
            WorkStatus.IN_PROGRESS,
            WorkStatus.COMPLETED);

    private static final Comparator<ProgressBoardInfo.Card> CARD_ORDER =
            Comparator.comparing(ProgressBoardInfo.Card::startTime).thenComparing(ProgressBoardInfo.Card::workId);

    private final LoadActorPort loadActorPort;
    private final WorkQueryPort workQueryPort;
    private final LoadTechnicianProfilesPort loadTechnicianProfilesPort;
    private final Clock clock;

    public GetProgressBoardService(
            LoadActorPort loadActorPort,
            WorkQueryPort workQueryPort,
            LoadTechnicianProfilesPort loadTechnicianProfilesPort,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workQueryPort = workQueryPort;
        this.loadTechnicianProfilesPort = loadTechnicianProfilesPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProgressBoardInfo get(GetProgressBoardQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        Instant now = clock.instant();

        List<Work> works = workQueryPort.listShownBetween(manager.organizationId(), period.from(), period.to());
        DisplayInfos displayInfos =
                DisplayInfos.ofAssignees(loadTechnicianProfilesPort, manager.organizationId(), works);
        List<ProgressBoardInfo.Column> columns = COLUMN_ORDER.stream()
                .map(status -> new ProgressBoardInfo.Column(
                        status,
                        works.stream()
                                .filter(work -> work.status() == status)
                                .map(work -> card(work, now, displayInfos))
                                .sorted(CARD_ORDER)
                                .toList()))
                .toList();
        return new ProgressBoardInfo(columns, summary(columns));
    }

    /** 열에 담긴 카드로 센다. 전체 수가 다섯 열 카드 수의 합과 늘 같다. */
    private static ProgressBoardInfo.Summary summary(List<ProgressBoardInfo.Column> columns) {
        List<ProgressBoardInfo.Card> cards =
                columns.stream().flatMap(column -> column.cards().stream()).toList();
        return new ProgressBoardInfo.Summary(
                cards.size(),
                cardCount(columns, WorkStatus.IN_PROGRESS),
                // 늦게 완료한 작업도 카드에는 지연으로 보이지만, 요약은 지금 지연 중인 작업만 센다.
                (int) columns.stream()
                        .filter(column -> column.status() != WorkStatus.COMPLETED)
                        .flatMap(column -> column.cards().stream())
                        .filter(ProgressBoardInfo.Card::delayed)
                        .count(),
                cardCount(columns, WorkStatus.COMPLETED));
    }

    private static ProgressBoardInfo.Card card(Work work, Instant now, DisplayInfos displayInfos) {
        ShownSchedule shown = work.shownSchedule().orElseThrow();
        TechnicianId technicianId = shown.technicianId();
        return new ProgressBoardInfo.Card(
                work.id().orElseThrow().value(),
                work.name(),
                technicianId == null ? null : displayInfos.technician(technicianId),
                shown.startTime(),
                shown.endTime().orElse(null),
                work.customerInfo().name().orElse(null),
                work.isDelayedAt(now),
                work.isReturnedByRejection());
    }

    private static int cardCount(List<ProgressBoardInfo.Column> columns, WorkStatus status) {
        return columns.stream()
                .filter(column -> column.status() == status)
                .mapToInt(column -> column.cards().size())
                .sum();
    }
}
