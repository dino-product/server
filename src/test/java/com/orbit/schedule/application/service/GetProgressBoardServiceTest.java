package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ASSIGNED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("진행 보드 조회")
class GetProgressBoardServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetProgressBoardService service = new GetProgressBoardService(
            fixture.actorPort, fixture.workRepository, fixture.technicianProfiles, fixture.clock);

    @Test
    @DisplayName("구간의 작업만 등록·배정됨·작업전·작업중·완료 열로 나누고 각 열은 시작시각·작업 식별자 순이며, 카드에 담당 기사·고객 이름을 담고 전체·작업중·지연·완료 수를 센다")
    void groupsWorksByStatus() {
        fixture.givenManager();
        fixture.technicianProfiles.given(ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김민수", null));
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        WorkId laterPending = fixture.givenWork(
                ORGANIZATION_ID, "나중 대기", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        WorkId pending =
                fixture.givenWork(ORGANIZATION_ID, "먼저 대기", WorkStatus.PENDING_ACCEPT, OTHER_TECHNICIAN_ID, TEN);
        WorkId lateInProgress =
                fixture.givenWork(ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart);
        WorkId completed =
                fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, pastStart);
        WorkId sameStartPending =
                fixture.givenWork(ORGANIZATION_ID, "같은 시각 대기", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELED, TECHNICIAN_ID, TEN);
        fixture.givenWork(WorkStatus.REGISTERED);
        fixture.givenWork(ORGANIZATION_ID, "구간 전에 끝난 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, FROM.minus(TWO_HOURS));
        fixture.givenWork(ORGANIZATION_ID, "구간 뒤에 시작하는 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns())
                .extracting(ProgressBoardInfo.Column::status)
                .containsExactly(
                        WorkStatus.REGISTERED,
                        WorkStatus.PENDING_ACCEPT,
                        WorkStatus.ACCEPTED,
                        WorkStatus.IN_PROGRESS,
                        WorkStatus.COMPLETED);
        assertThat(board.columns().get(0).cards()).as("시간이 없는 대기함 작업은 담지 않는다").isEmpty();
        assertThat(board.columns().get(1).cards())
                .extracting(ProgressBoardInfo.Card::workId)
                .containsExactly(pending.value(), sameStartPending.value(), laterPending.value());
        assertThat(board.columns().get(2).cards()).isEmpty();
        assertThat(board.columns().get(3).cards())
                .containsExactly(new ProgressBoardInfo.Card(
                        lateInProgress.value(),
                        "늦은 작업",
                        new TechnicianInfo(TECHNICIAN_ID.value(), "김민수", null),
                        pastStart,
                        pastStart.plus(TWO_HOURS),
                        "홍길동",
                        true,
                        false));
        assertThat(board.columns().get(4).cards())
                .extracting(
                        ProgressBoardInfo.Card::workId,
                        card -> card.technician().name(),
                        ProgressBoardInfo.Card::delayed)
                .as("표시 정보를 찾지 못한 기사는 이름 없이 식별자만 담는다")
                .containsExactly(tuple(completed.value(), null, false));
        assertThat(fixture.technicianProfiles.lookups())
                .as("카드의 담당 기사만 한 번에 찾는다")
                .containsExactly(Set.of(TECHNICIAN_ID, OTHER_TECHNICIAN_ID));
        assertThat(fixture.staffProfiles.lookups()).isEmpty();
        assertThat(fixture.workTypeLabels.lookups()).isEmpty();
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(5, 1, 1, 1));
    }

    @Test
    @DisplayName("수락대기·수락됨 작업도 예정 종료시각이 지나면 지연 수에 든다")
    void countsDelayedWaitingWorks() {
        fixture.givenManager();
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        fixture.givenWork(ORGANIZATION_ID, "늦은 대기", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, pastStart);
        fixture.givenWork(ORGANIZATION_ID, "늦은 수락", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, pastStart);

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns().get(1).cards())
                .singleElement()
                .extracting(ProgressBoardInfo.Card::delayed)
                .isEqualTo(true);
        assertThat(board.columns().get(2).cards())
                .singleElement()
                .extracting(ProgressBoardInfo.Card::delayed)
                .isEqualTo(true);
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(2, 0, 2, 0));
    }

    @Test
    @DisplayName("예정 종료시각 뒤에 완료한 작업은 카드에는 지연으로 보이지만 요약의 지연 수에는 들지 않는다")
    void showsLateCompletionWithoutCountingItAsDelayed() {
        fixture.givenManager();
        // 완료는 ACCEPTED_AT(NOW - 1.5시간)이고 예정 종료는 NOW - 2시간이다.
        fixture.givenWork(
                ORGANIZATION_ID, "늦게 완료", WorkStatus.COMPLETED, TECHNICIAN_ID, NOW.minus(Duration.ofHours(4)));

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns().get(4).cards())
                .singleElement()
                .extracting(ProgressBoardInfo.Card::delayed)
                .isEqualTo(true);
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(1, 0, 0, 1));
    }

    @Test
    @DisplayName("작업이 없으면 빈 열 다섯 개와 0 집계를 돌려준다")
    void returnsEmptyBoard() {
        fixture.givenManager();

        ProgressBoardInfo board = service.get(query(FROM, TO));

        assertThat(board.columns()).hasSize(5).allSatisfy(column -> assertThat(column.cards())
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

    @Test
    @DisplayName("고객 이름이 없는 작업의 카드는 고객 이름을 비운다")
    void leavesCustomerNameEmpty() {
        fixture.givenManager();
        Work work = Work.register(ORGANIZATION_ID, "고객 없는 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), ASSIGNED_AT, REGISTRAR_ID);
        fixture.workRepository.store(work);

        assertThat(service.get(query(FROM, TO)).columns().get(1).cards())
                .singleElement()
                .extracting(ProgressBoardInfo.Card::customerName)
                .isNull();
    }

    @Test
    @DisplayName("미리 입력한 시간이나 마지막 배정 시간이 구간에 든 대기함 작업을 등록 열에 담고, 거절로 돌아온 작업과 지연을 표시해 지연 수에 센다")
    void showsBacklogWorksWithTimeInPeriod() {
        fixture.givenManager();
        fixture.technicianProfiles.given(ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김민수", null));
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        WorkId plannedLate = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "시간만 정한 작업",
                REGISTRAR_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(null, pastStart, TWO_HOURS)));
        WorkId startOnly = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "시작만 정한 작업",
                REGISTRAR_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(TECHNICIAN_ID, TEN, null)));
        fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "기사만 정한 작업",
                REGISTRAR_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(TECHNICIAN_ID, null, null)));
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ASSIGNED_AT.plusSeconds(60));
        WorkId rejectedId = fixture.workRepository.store(rejected);

        ProgressBoardInfo board = service.get(query(FROM, TO));

        TechnicianInfo kim = new TechnicianInfo(TECHNICIAN_ID.value(), "김민수", null);
        assertThat(board.columns().getFirst().cards())
                .containsExactly(
                        new ProgressBoardInfo.Card(
                                plannedLate.value(),
                                "시간만 정한 작업",
                                null,
                                pastStart,
                                pastStart.plus(TWO_HOURS),
                                null,
                                true,
                                false),
                        new ProgressBoardInfo.Card(startOnly.value(), "시작만 정한 작업", kim, TEN, null, null, false, false),
                        new ProgressBoardInfo.Card(
                                rejectedId.value(), "대상 작업", kim, TEN, TEN.plus(TWO_HOURS), "홍길동", false, true));
        assertThat(board.summary()).isEqualTo(new ProgressBoardInfo.Summary(3, 0, 1, 0));
    }

    private static GetProgressBoardQuery query(Instant from, Instant to) {
        return new GetProgressBoardQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to);
    }
}
