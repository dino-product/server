package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
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
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("기사 작업 이력·통계 조회")
class GetWorkHistoryServiceTest {

    private static final Instant FROM = TEN.minus(Duration.ofDays(1));
    private static final Instant TO = TEN.plus(Duration.ofDays(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetWorkHistoryService service = new GetWorkHistoryService(fixture.actorPort, fixture.workRepository);

    @Test
    @DisplayName("구간 안 배정이 있는 작업을 지금 상태와 관계없이 최근 순으로 담고, 기사의 마지막 배정과 완료·거절·취소 수를 준다")
    void listsHistoryAndStatistics() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "완료한 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        Work rejected = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절한 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, at(1)));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        Work reassigned =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "넘겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(2)));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        WorkId cancelled = fixture.givenWork(ORGANIZATION_ID, "취소된 작업", WorkStatus.CANCELLED, TECHNICIAN_ID, at(3));
        Work rescheduled = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "시간 바뀐 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(4)));
        rescheduled.reschedule(at(5), TWO_HOURS, NOW, SETUP_MANAGER_ID);
        WorkId rescheduledId = fixture.workRepository.store(rescheduled);
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "구간 밖 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        WorkHistoryInfo history = service.get(query(null, FROM, TO));

        assertThat(history.technicianId()).isEqualTo(TECHNICIAN_ID.value());
        assertThat(history.works())
                .extracting(
                        WorkHistoryInfo.AssignedWork::workId,
                        WorkHistoryInfo.AssignedWork::status,
                        WorkHistoryInfo.AssignedWork::current,
                        WorkHistoryInfo.AssignedWork::assignmentNumber,
                        WorkHistoryInfo.AssignedWork::result,
                        WorkHistoryInfo.AssignedWork::endReason)
                .containsExactly(
                        tuple(
                                rescheduledId.value(),
                                WorkStatus.PENDING_ACCEPTANCE,
                                true,
                                2,
                                AssignmentResult.PENDING,
                                null),
                        tuple(
                                cancelled.value(),
                                WorkStatus.CANCELLED,
                                true,
                                1,
                                AssignmentResult.WITHDRAWN,
                                AssignmentEndReason.CANCELLED),
                        tuple(
                                reassignedId.value(),
                                WorkStatus.PENDING_ACCEPTANCE,
                                false,
                                1,
                                AssignmentResult.ACCEPTED,
                                AssignmentEndReason.REASSIGNED),
                        tuple(rejectedId.value(), WorkStatus.REGISTERED, false, 1, AssignmentResult.REJECTED, null),
                        tuple(completed.value(), WorkStatus.COMPLETED, true, 1, AssignmentResult.ACCEPTED, null));
        assertThat(history.works().getFirst().startTime()).isEqualTo(at(5));
        assertThat(history.works().getFirst().endTime()).isEqualTo(at(5).plus(TWO_HOURS));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(5, 1, 1, 1));
    }

    @Test
    @DisplayName("구간 밖의 배정은 마지막 배정·거절 수에 넣지 않는다")
    void ignoresAssignmentsOutsidePeriod() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TO.plus(TWO_HOURS), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        fixture.workRepository.store(work);

        WorkHistoryInfo history = service.get(query(null, FROM, TO));

        assertThat(history.works())
                .extracting(WorkHistoryInfo.AssignedWork::assignmentNumber, WorkHistoryInfo.AssignedWork::result)
                .containsExactly(tuple(1, AssignmentResult.REJECTED));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(1, 0, 1, 0));
        assertThat(service.get(query(null, TO, TO.plus(Duration.ofDays(1)))).statistics())
                .isEqualTo(new WorkHistoryInfo.Statistics(1, 0, 0, 0));
    }

    @Test
    @DisplayName("구간 밖으로 일정이 옮겨졌거나 다시 배정된 작업의 구간 안 배정은 지금 담당이 아니고, 완료는 그 배정이 속한 구간에서 한 번만 센다")
    void marksCurrentOnlyForLatestAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Instant later = TO.plus(Duration.ofHours(2));
        Work movedOut =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "옮겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN));
        movedOut.reschedule(later, TWO_HOURS, NOW, SETUP_MANAGER_ID);
        movedOut.accept(NOW);
        movedOut.start(NOW);
        movedOut.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId movedOutId = fixture.workRepository.store(movedOut);
        Work returned =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "돌아온 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(1)));
        returned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(1), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        returned.reassign(new WorkSchedule(TECHNICIAN_ID, later, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId returnedId = fixture.workRepository.store(returned);

        WorkHistoryInfo inPeriod = service.get(query(null, FROM, TO));
        WorkHistoryInfo nextPeriod = service.get(query(null, TO, TO.plus(Duration.ofDays(1))));

        assertThat(inPeriod.works())
                .extracting(
                        WorkHistoryInfo.AssignedWork::workId,
                        WorkHistoryInfo.AssignedWork::current,
                        WorkHistoryInfo.AssignedWork::assignmentNumber)
                .containsExactly(tuple(returnedId.value(), false, 1), tuple(movedOutId.value(), false, 1));
        assertThat(inPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 0, 0, 0));
        assertThat(nextPeriod.works())
                .extracting(
                        WorkHistoryInfo.AssignedWork::workId,
                        WorkHistoryInfo.AssignedWork::current,
                        WorkHistoryInfo.AssignedWork::assignmentNumber)
                .containsExactly(tuple(returnedId.value(), true, 3), tuple(movedOutId.value(), true, 2));
        assertThat(nextPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 1, 0, 0));
    }

    @Test
    @DisplayName("구간 경계에 걸친 일정은 두 구간 목록에 모두 나오지만 완료·거절은 시작이 속한 구간에서만 센다")
    void countsBoundaryWorkOnlyInStartPeriod() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Instant crossingStart = TO.minus(Duration.ofHours(1));
        WorkId crossing =
                fixture.givenWork(ORGANIZATION_ID, "밤샘 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, crossingStart);
        Work rejected = fixture.stored(fixture.givenWork(
                ORGANIZATION_ID, "밤샘 거절", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, crossingStart));
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);

        WorkHistoryInfo startPeriod = service.get(query(null, FROM, TO));
        WorkHistoryInfo endPeriod = service.get(query(null, TO, TO.plus(Duration.ofDays(1))));

        assertThat(startPeriod.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value(), crossing.value());
        assertThat(endPeriod.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value(), crossing.value());
        assertThat(startPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 1, 1, 0));
        assertThat(endPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 0, 0, 0));
    }

    @Test
    @DisplayName("대기함으로 되돌린 취소는 배정 이력에 취소 종료로 남지만 지금 담당도 취소도 아니다")
    void treatsRestoredCancellationAsNotCurrent() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.CANCELLED));
        work.correctStatus(WorkStatus.REGISTERED, NOW, new MembershipId(13L), "잘못 취소");
        WorkId id = fixture.workRepository.store(work);

        WorkHistoryInfo history = service.get(query(null, FROM, TO));

        assertThat(history.works())
                .extracting(
                        WorkHistoryInfo.AssignedWork::workId,
                        WorkHistoryInfo.AssignedWork::status,
                        WorkHistoryInfo.AssignedWork::current,
                        WorkHistoryInfo.AssignedWork::endReason)
                .containsExactly(tuple(id.value(), WorkStatus.REGISTERED, false, AssignmentEndReason.CANCELLED));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(1, 0, 0, 0));
    }

    @Test
    @DisplayName("배정 시작시각이 같으면 작업 식별자 내림차순이다")
    void ordersSameStartByIdDescending() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "첫 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId second = fixture.givenWork(ORGANIZATION_ID, "둘째 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(null, FROM, TO)).works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(second.value(), first.value());
    }

    @Test
    @DisplayName("관리자는 기사를 지정해 이력을 보고, 지정하지 않으면 입력 오류다")
    void managerReadsGivenTechnician() {
        fixture.givenManager();
        WorkId work = fixture.givenWork(WorkStatus.COMPLETED);

        assertThat(service.get(query(TECHNICIAN_ID.value(), FROM, TO)).works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(work.value());
        assertThat(service.get(query(OTHER_TECHNICIAN_ID.value(), FROM, TO)).works())
                .isEmpty();
        fixture.assertRejected(() -> service.get(query(null, FROM, TO)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(0L, FROM, TO)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("기사는 본인만 지정할 수 있고, 다른 기사를 지정하면 구간이 틀려도 권한 오류다")
    void technicianReadsOnlyOwnHistory() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(WorkStatus.COMPLETED);

        assertThat(service.get(query(TECHNICIAN_ID.value(), FROM, TO)).works()).hasSize(1);
        fixture.assertRejected(
                () -> service.get(query(OTHER_TECHNICIAN_ID.value(), null, null)),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.assertRejected(() -> service.get(query(0L, FROM, TO)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("계정 → 조직 식별자 → 구성원 → 기사 식별자 → 구간 순서로 확인한다")
    void checksOrder() {
        assertThatThrownBy(() -> service.get(new GetWorkHistoryQuery(null, 0L, 0L, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetWorkHistoryQuery(ACCOUNT_ID, 0L, 0L, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(0L, null, null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.givenManager();
        fixture.assertRejected(
                () -> service.get(query(TECHNICIAN_ID.value(), TO, FROM)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    /** {@link ScheduleServiceFixture#TEN}부터 n시간 뒤. 작업마다 시작시각을 달리해 최근 순 정렬을 드러낸다. */
    private static Instant at(int hours) {
        return TEN.plus(Duration.ofHours(hours));
    }

    private static GetWorkHistoryQuery query(Long technicianId, Instant from, Instant to) {
        return new GetWorkHistoryQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), technicianId, from, to);
    }
}
