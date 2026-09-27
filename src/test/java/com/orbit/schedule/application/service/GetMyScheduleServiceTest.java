package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
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
import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("기사 본인 일정 조회")
class GetMyScheduleServiceTest {

    private static final Instant FROM = NOW.minus(Duration.ofHours(4));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetMyScheduleService service =
            new GetMyScheduleService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("지금 본인이 담당인 구간 안 작업을 시작시각 순으로 담고(다른 기사로 바뀌었거나 거절로 대기함에 돌아간 작업 제외), 지금 배정의 순번·현장 주소·지연을 표시한다")
    void listsOwnWorks() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        WorkId pending = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);
        Work rescheduled = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "시간 바뀐 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, pastStart));
        rescheduled.reschedule(pastStart.minusSeconds(60), TWO_HOURS, NOW, SETUP_MANAGER_ID);
        WorkId rescheduledId = fixture.workRepository.store(rescheduled);
        WorkId completed =
                fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN.plus(TWO_HOURS));
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "취소 작업", WorkStatus.CANCELLED, TECHNICIAN_ID, TEN);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        fixture.workRepository.store(reassigned);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId inProgress = fixture.givenWork(
                ORGANIZATION_ID, "진행 중 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN.plus(Duration.ofHours(1)));
        Work reassignedBack = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassignedBack.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        reassignedBack.reassign(new WorkSchedule(TECHNICIAN_ID, TEN.plus(TWO_HOURS), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        reassignedBack.accept(NOW);
        WorkId reassignedBackId = fixture.workRepository.store(reassignedBack);
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), NOW);
        fixture.workRepository.store(rejected);

        MyScheduleInfo schedule = service.get(query(FROM, TO));

        assertThat(schedule.works())
                .extracting(
                        MyScheduleInfo.ScheduledWork::workId,
                        MyScheduleInfo.ScheduledWork::status,
                        MyScheduleInfo.ScheduledWork::assignmentNumber,
                        MyScheduleInfo.ScheduledWork::delayed)
                .containsExactly(
                        tuple(rescheduledId.value(), WorkStatus.PENDING_ACCEPTANCE, 2, true),
                        tuple(pending.value(), WorkStatus.PENDING_ACCEPTANCE, 1, false),
                        tuple(inProgress.value(), WorkStatus.IN_PROGRESS, 1, false),
                        tuple(completed.value(), WorkStatus.COMPLETED, 1, false),
                        tuple(reassignedBackId.value(), WorkStatus.ACCEPTED, 3, false));
        assertThat(schedule.works().get(1))
                .isEqualTo(new MyScheduleInfo.ScheduledWork(
                        pending.value(),
                        "대상 작업",
                        WorkStatus.PENDING_ACCEPTANCE,
                        1,
                        TEN,
                        TEN.plus(TWO_HOURS),
                        "서울시",
                        false));
    }

    @Test
    @DisplayName("지금 담당인 작업이 없으면 빈 목록이다")
    void returnsEmptySchedule() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);

        assertThat(service.get(query(FROM, TO)).works()).isEmpty();
    }

    @Test
    @DisplayName("관리자는 요청할 수 없고, 계정 → 조직 식별자 → 구성원 → 요청자 종류 → 구간 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetMyScheduleQuery(null, 0L, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetMyScheduleQuery(ACCOUNT_ID, 0L, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenManager(ActorRole.OWNER);
        fixture.assertRejected(() -> service.get(query(null, null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(() -> service.get(query(TO, FROM)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static GetMyScheduleQuery query(Instant from, Instant to) {
        return new GetMyScheduleQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), from, to);
    }
}
