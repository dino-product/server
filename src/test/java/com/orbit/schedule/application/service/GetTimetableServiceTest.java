package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
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
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("타임테이블 조회")
class GetTimetableServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetTimetableService service =
            new GetTimetableService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("구간과 일정이 겹치는 취소 외 작업을 기사·시작시각 순으로 담고, 같은 기사의 겹친 활성 작업과 지연을 표시한다")
    void listsScheduledWorks() {
        fixture.givenManager();
        WorkId later = fixture.givenWork(
                ORGANIZATION_ID, "겹친 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "먼저 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId otherTechnician =
                fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, TEN);
        WorkId late = fixture.givenWork(
                ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, NOW.minus(Duration.ofHours(3)));
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELLED, TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "구간 밖 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        TimetableInfo timetable = service.get(query(FROM, TO));

        assertThat(timetable.scheduledWorks())
                .extracting(
                        TimetableInfo.ScheduledWork::workId,
                        TimetableInfo.ScheduledWork::technicianId,
                        TimetableInfo.ScheduledWork::status,
                        TimetableInfo.ScheduledWork::delayed,
                        TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(
                        tuple(first.value(), TECHNICIAN_ID.value(), WorkStatus.ACCEPTED, false, true),
                        tuple(later.value(), TECHNICIAN_ID.value(), WorkStatus.PENDING_ACCEPTANCE, false, true),
                        tuple(late.value(), OTHER_TECHNICIAN_ID.value(), WorkStatus.IN_PROGRESS, true, false),
                        tuple(
                                otherTechnician.value(),
                                OTHER_TECHNICIAN_ID.value(),
                                WorkStatus.COMPLETED,
                                false,
                                false));
        assertThat(timetable.scheduledWorks().getFirst())
                .isEqualTo(new TimetableInfo.ScheduledWork(
                        first.value(),
                        "먼저 작업",
                        WorkStatus.ACCEPTED,
                        TECHNICIAN_ID.value(),
                        TEN,
                        TEN.plus(TWO_HOURS),
                        false,
                        true));
    }

    @Test
    @DisplayName("완료된 작업과 겹쳐도 활성 작업끼리가 아니면 겹침으로 표시하지 않는다")
    void ignoresOverlapWithFinishedWork() {
        fixture.givenManager();
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "완료 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        WorkId accepted = fixture.givenWork(ORGANIZATION_ID, "수락 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactlyInAnyOrder(tuple(completed.value(), false), tuple(accepted.value(), false));
    }

    @Test
    @DisplayName("겹치는 짝이 구간 밖에 있어도 겹침으로 표시하고, 짝은 칸에 넣지 않는다")
    void marksConflictWithWorkOutsidePeriod() {
        fixture.givenManager();
        // 구간 전 작업(TEN ~ +2h)은 구간 시작(+2h) 전에 끝나고, 구간에 걸친 작업(+1h ~ +3h)과는 겹친다.
        fixture.givenWork(ORGANIZATION_ID, "구간 전 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId inside = fixture.givenWork(
                ORGANIZATION_ID, "구간에 걸친 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));
        Instant from = TEN.plus(TWO_HOURS);

        TimetableInfo timetable = service.get(query(from, from.plus(Duration.ofDays(1))));

        assertThat(timetable.scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(tuple(inside.value(), true));
    }

    @Test
    @DisplayName("같은 기사의 이어 붙은 작업은 겹침이 아니고, 시작시각이 같으면 작업 식별자 순이다")
    void ordersAndIgnoresAdjacentWorks() {
        fixture.givenManager();
        WorkId next =
                fixture.givenWork(ORGANIZATION_ID, "이어지는 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "앞 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId sameStartLater =
                fixture.givenWork(ORGANIZATION_ID, "같은 시각 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).scheduledWorks())
                .extracting(TimetableInfo.ScheduledWork::workId, TimetableInfo.ScheduledWork::conflicting)
                .containsExactly(
                        tuple(first.value(), false), tuple(sameStartLater.value(), false), tuple(next.value(), false));
    }

    @Test
    @DisplayName("대기함 작업은 등록 순으로 담고, 기사가 거절해 돌아온 작업을 표시한다")
    void listsBacklog() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId fresh = fixture.givenWork(WorkStatus.REGISTERED);
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejected.reject(new Rejection(RejectionReason.LOCATION_TOO_FAR, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.REGISTERED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).backlog())
                .containsExactly(
                        new TimetableInfo.BacklogWork(fresh.value(), "대상 작업", 2L, false),
                        new TimetableInfo.BacklogWork(rejectedId.value(), "대상 작업", 2L, true));
    }

    @Test
    @DisplayName("구간이 없거나 뒤집혔거나 비었거나 31일을 넘으면 입력 오류다")
    void rejectsInvalidPeriod() {
        fixture.givenManager();

        for (Instant[] period : new Instant[][] {
            {null, TO},
            {FROM, null},
            {TO, FROM},
            {FROM, FROM},
            {FROM, FROM.plus(Duration.ofDays(31)).plusNanos(1_000)}
        }) {
            fixture.assertRejected(
                    () -> service.get(query(period[0], period[1])), ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        assertThat(service.get(query(FROM, FROM.plus(Duration.ofDays(31)))).scheduledWorks())
                .isEmpty();
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 요청자 종류(기사 불가) → 구간 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetTimetableQuery(null, 0L, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetTimetableQuery(ACCOUNT_ID, 0L, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    private static GetTimetableQuery query(Instant from, Instant to) {
        return new GetTimetableQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to);
    }
}
