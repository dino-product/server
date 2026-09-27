package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("진행 보드 조회")
class GetProgressBoardServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetProgressBoardService service =
            new GetProgressBoardService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("구간의 작업만 수락대기·수락됨·작업중·완료 열로 나누고 각 열은 시작시각·작업 식별자 순이며, 전체·완료·작업중·지연 수를 센다")
    void groupsWorksByStatus() {
        fixture.givenManager();
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        WorkId laterPending = fixture.givenWork(
                ORGANIZATION_ID, "나중 대기", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        WorkId pending =
                fixture.givenWork(ORGANIZATION_ID, "먼저 대기", WorkStatus.PENDING_ACCEPTANCE, OTHER_TECHNICIAN_ID, TEN);
        WorkId lateInProgress =
                fixture.givenWork(ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart);
        WorkId completed =
                fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, pastStart);
        WorkId sameStartPending =
                fixture.givenWork(ORGANIZATION_ID, "같은 시각 대기", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELLED, TECHNICIAN_ID, TEN);
        fixture.givenWork(WorkStatus.REGISTERED);
        fixture.givenWork(ORGANIZATION_ID, "구간 전에 끝난 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, FROM.minus(TWO_HOURS));
        fixture.givenWork(ORGANIZATION_ID, "구간 뒤에 시작하는 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns())
                .extracting(ProgressBoardInfo.Column::status)
                .containsExactly(
                        WorkStatus.PENDING_ACCEPTANCE,
                        WorkStatus.ACCEPTED,
                        WorkStatus.IN_PROGRESS,
                        WorkStatus.COMPLETED);
        assertThat(board.columns().get(0).cards())
                .extracting(ProgressBoardInfo.Card::workId)
                .containsExactly(pending.value(), sameStartPending.value(), laterPending.value());
        assertThat(board.columns().get(1).cards()).isEmpty();
        assertThat(board.columns().get(2).cards())
                .containsExactly(new ProgressBoardInfo.Card(
                        lateInProgress.value(),
                        "늦은 작업",
                        TECHNICIAN_ID.value(),
                        pastStart,
                        pastStart.plus(TWO_HOURS),
                        true));
        assertThat(board.columns().get(3).cards())
                .extracting(ProgressBoardInfo.Card::workId, ProgressBoardInfo.Card::delayed)
                .containsExactly(tuple(completed.value(), false));
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(5, 1, 1, 1));
    }

    @Test
    @DisplayName("작업이 없으면 빈 열 네 개와 0 집계를 돌려준다")
    void returnsEmptyBoard() {
        fixture.givenManager();

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns()).hasSize(4).allSatisfy(column -> assertThat(column.cards())
                .isEmpty());
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(0, 0, 0, 0));
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 구간 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetProgressBoardQuery(null, 0L, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetProgressBoardQuery(ACCOUNT_ID, 0L, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.givenManager();
        fixture.assertRejected(
                () -> service.get(query(FROM, FROM.plus(Duration.ofDays(32)))), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static GetProgressBoardQuery query(Instant from, Instant to) {
        return new GetProgressBoardQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to);
    }
}
