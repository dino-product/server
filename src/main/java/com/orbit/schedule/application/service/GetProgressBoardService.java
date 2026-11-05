package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.ProgressBoardBusyException;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshot;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshotPort;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 관리자 진행 보드 조회. 보일 일정({@link com.orbit.schedule.domain.Work#shownSchedule})이 구간과 겹치는 작업(등록·배정됨·작업전·작업중·완료)을
 * 지금 상태 그대로 열로 나누고(별도의 '이동중' 상태는 두지 않는다) 전체·작업중·지연·완료 수를 센다. 등록 열은 미리 입력한 시간이나 마지막 배정 시간이 구간에 든
 * 대기함 작업이고, 거절로 돌아온 작업을 카드에 표시한다. 카드에 보일 담당 기사만 표시 정보를 찾는다. 지연은 지금 시각으로 판정한다. 기사는 요청할 수 없다(403 SCHEDULE-005). 오류 확인
 * 순서는 요청자 → 구간 입력이다.
 *
 * <p>카드는 {@link ProgressBoardSnapshotPort}에서 얻는다(같은 조직의 화면들이 나눠 쓰는 스냅샷). 스냅샷에는 조회 시각과 상관없는 값만 있으므로 지연 여부와
 * 요약 수는 여기서 지금 시각으로 계산한다. 지금 보드를 만들 수 없으면 잠시 뒤 다시 조회하라는 503(COMMON-503)이다.
 *
 * <p>이 유즈케이스는 트랜잭션을 열지 않는다. 같은 보드를 만드는 다른 요청을 기다리는 동안 DB 커넥션을 잡지 않기 위해서다. 요청자 확인은 organization 조회가,
 * 스냅샷 만들기는 {@link ProgressBoardSnapshotReader}가 각자 읽기 전용 트랜잭션을 연다.
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
    private final ProgressBoardSnapshotPort snapshotPort;
    private final ProgressBoardSnapshotReader snapshotReader;
    private final Clock clock;

    public GetProgressBoardService(
            LoadActorPort loadActorPort,
            ProgressBoardSnapshotPort snapshotPort,
            ProgressBoardSnapshotReader snapshotReader,
            Clock clock) {
        this.loadActorPort = loadActorPort;
        this.snapshotPort = snapshotPort;
        this.snapshotReader = snapshotReader;
        this.clock = clock;
    }

    @Override
    public ProgressBoardInfo get(GetProgressBoardQuery query) {
        ManagerActor manager =
                OrganizationActors.requireManager(loadActorPort, query.accountId(), query.organizationId());
        QueryPeriod period = QueryPeriod.of(query.from(), query.to());
        OrganizationId organizationId = manager.organizationId();

        ProgressBoardSnapshot snapshot;
        try {
            snapshot = snapshotPort.load(
                    organizationId,
                    period.from(),
                    period.to(),
                    () -> snapshotReader.read(organizationId, period.from(), period.to()));
        } catch (ProgressBoardBusyException exception) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        Instant now = clock.instant();
        List<ProgressBoardInfo.Column> columns = COLUMN_ORDER.stream()
                .map(status -> new ProgressBoardInfo.Column(
                        status,
                        snapshot.cards().stream()
                                .filter(card -> card.status() == status)
                                .map(card -> card(card, now))
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

    private static ProgressBoardInfo.Card card(ProgressBoardSnapshot.Card card, Instant now) {
        return new ProgressBoardInfo.Card(
                card.workId(),
                card.name(),
                card.technician(),
                card.startTime(),
                card.endTime(),
                card.customerName(),
                card.delayedAt(now),
                card.returnedByRejection());
    }

    private static int cardCount(List<ProgressBoardInfo.Column> columns, WorkStatus status) {
        return columns.stream()
                .filter(column -> column.status() == status)
                .mapToInt(column -> column.cards().size())
                .sum();
    }
}
