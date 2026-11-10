package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ASSIGNED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryStatus;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CompletionReport;
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
    private final GetWorkHistoryService service =
            new GetWorkHistoryService(fixture.actorPort, fixture.workRepository, fixture.workTypeLabels);

    @Test
    @DisplayName("관리자에게는 구간 안 배정이 있는 작업을 지금 상태와 관계없이 최근 순으로 담고, 기사의 마지막 배정과 완료·거절·취소 수를 준다")
    void listsHistoryAndStatistics() {
        fixture.givenManager();
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "완료한 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        Work rejected = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절한 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(1)));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        Work reassigned =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "넘겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(2)));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        WorkId cancelled = fixture.givenWork(ORGANIZATION_ID, "취소된 작업", WorkStatus.CANCELED, TECHNICIAN_ID, at(3));
        Work rescheduled = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "시간 바뀐 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(4)));
        rescheduled.reschedule(at(5), TWO_HOURS, NOW, SETUP_MANAGER_ID);
        WorkId rescheduledId = fixture.workRepository.store(rescheduled);
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "구간 밖 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TO);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        WorkHistoryInfo history = service.get(query(TECHNICIAN_ID.value(), FROM, TO));

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
                        tuple(rescheduledId.value(), WorkStatus.ACCEPTED, true, 2, AssignmentResult.ACCEPTED, null),
                        tuple(
                                cancelled.value(),
                                WorkStatus.CANCELED,
                                true,
                                1,
                                AssignmentResult.WITHDRAWN,
                                AssignmentEndReason.CANCELLED),
                        tuple(
                                reassignedId.value(),
                                WorkStatus.PENDING_ACCEPT,
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
    @DisplayName("기사 본인에게는 본인이 완료한 작업만 담고 통계도 그 작업으로만 세어 거절·취소 수는 0이다")
    void technicianSeesOnlyOwnCompletedWorks() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "완료한 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        Work rejected = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절한 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(1)));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        fixture.workRepository.store(rejected);
        Work reassigned =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "넘겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(2)));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        fixture.workRepository.store(reassigned);
        fixture.givenWork(ORGANIZATION_ID, "취소된 작업", WorkStatus.CANCELED, TECHNICIAN_ID, at(3));
        fixture.givenWork(ORGANIZATION_ID, "작업중인 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, at(4));
        Work rejectedThenCompleted = fixture.stored(fixture.givenWork(
                ORGANIZATION_ID, "거절 뒤 다시 받아 완료한 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(5)));
        rejectedThenCompleted.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        rejectedThenCompleted.assign(new WorkSchedule(TECHNICIAN_ID, at(6), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        rejectedThenCompleted.accept(NOW);
        rejectedThenCompleted.start(NOW);
        rejectedThenCompleted.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId rejectedThenCompletedId = fixture.workRepository.store(rejectedThenCompleted);
        Work completedByOther = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "넘겨진 뒤 완료된 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(7)));
        completedByOther.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(7), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        completedByOther.accept(NOW);
        completedByOther.start(NOW);
        completedByOther.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        fixture.workRepository.store(completedByOther);

        WorkHistoryInfo history = service.get(query(null, FROM, TO));

        assertThat(history.works())
                .extracting(
                        WorkHistoryInfo.AssignedWork::workId,
                        WorkHistoryInfo.AssignedWork::status,
                        WorkHistoryInfo.AssignedWork::current,
                        WorkHistoryInfo.AssignedWork::assignmentNumber)
                .containsExactly(
                        tuple(rejectedThenCompletedId.value(), WorkStatus.COMPLETED, true, 2),
                        tuple(completed.value(), WorkStatus.COMPLETED, true, 1));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 2, 0, 0));
    }

    @Test
    @DisplayName("누적 완료 수는 구간과 관계없이 그 기사가 지금 담당인 채 완료한 그 조직의 작업 수이고, 기사 본인과 관리자에게 같다")
    void countsTotalCompletedRegardlessOfPeriod() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(ORGANIZATION_ID, "구간 안 완료", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        fixture.givenWork(
                ORGANIZATION_ID, "지난달 완료", WorkStatus.COMPLETED, TECHNICIAN_ID, FROM.minus(Duration.ofDays(40)));
        fixture.givenWork(ORGANIZATION_ID, "작업중", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, at(1));
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 완료", WorkStatus.COMPLETED, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 완료", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        Work completedByOther = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "넘겨진 뒤 완료된 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(2)));
        completedByOther.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        completedByOther.accept(NOW);
        completedByOther.start(NOW);
        completedByOther.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        fixture.workRepository.store(completedByOther);

        assertThat(service.get(query(null, FROM, TO)).totalCompleted()).isEqualTo(2);
        assertThat(service.get(query(null, TO, TO.plus(Duration.ofDays(1)))).totalCompleted())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("작업유형·상태(완료·거절) 조건은 목록만 거르고 통계·누적 완료는 거르지 않으며, 완료한 작업에만 실제 시작·완료 시각과 유형을 담는다")
    void filtersListByWorkTypeAndHistoryStatus() {
        fixture.givenManager();
        fixture.workTypeLabels.given(ORGANIZATION_ID, new TypeLabel(WORK_TYPE_ID.value(), "설치", null, false));
        WorkId completed = fixture.givenWork(ORGANIZATION_ID, "설치 완료", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);
        Work rejected = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(1)));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);
        WorkId accepted = fixture.givenWork(ORGANIZATION_ID, "작업전", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(2));
        Work untyped = Work.register(ORGANIZATION_ID, "유형 없는 완료", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        untyped.assign(new WorkSchedule(TECHNICIAN_ID, at(3), TWO_HOURS), ASSIGNED_AT, SETUP_MANAGER_ID);
        untyped.accept(ACCEPTED_AT);
        untyped.start(ACCEPTED_AT);
        untyped.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId untypedId = fixture.workRepository.store(untyped);

        WorkHistoryInfo all = service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of()));
        WorkHistoryInfo installs =
                service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(WORK_TYPE_ID.value()), Set.of()));
        WorkHistoryInfo completedOnly =
                service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of(WorkHistoryStatus.COMPLETED)));
        WorkHistoryInfo rejectedOnly =
                service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of(WorkHistoryStatus.REJECTED)));

        assertThat(all.works()).hasSize(4);
        assertThat(installs.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(accepted.value(), rejectedId.value(), completed.value());
        assertThat(completedOnly.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(untypedId.value(), completed.value());
        assertThat(rejectedOnly.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value());
        assertThat(installs.statistics()).isEqualTo(all.statistics());
        assertThat(rejectedOnly.totalCompleted())
                .isEqualTo(all.totalCompleted())
                .isEqualTo(2);

        assertThat(all.works())
                .filteredOn(work -> work.workId().equals(completed.value()))
                .singleElement()
                .satisfies(work -> {
                    assertThat(work.workType().name()).isEqualTo("설치");
                    assertThat(work.startedAt()).isEqualTo(ACCEPTED_AT);
                    assertThat(work.completedAt()).isEqualTo(ACCEPTED_AT);
                });
        assertThat(all.works())
                .filteredOn(work -> !work.workId().equals(completed.value())
                        && !work.workId().equals(untypedId.value()))
                .allSatisfy(work -> {
                    assertThat(work.startedAt()).isNull();
                    assertThat(work.completedAt()).isNull();
                });
        assertThat(all.works())
                .filteredOn(work -> work.workId().equals(untypedId.value()))
                .singleElement()
                .satisfies(work -> assertThat(work.workType()).isNull());
    }

    @Test
    @DisplayName("작업유형 조건은 기사 확인·구간 뒤에 확인해, 남의 이력을 지정한 기사는 유형이 틀려도 권한 오류다")
    void checksWorkTypeIdsAfterTechnicianAndPeriod() {
        fixture.givenTechnician(TECHNICIAN_ID);

        fixture.assertRejected(
                () -> service.get(query(OTHER_TECHNICIAN_ID.value(), FROM, TO, Set.of(0L), Set.of())),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.assertRejected(
                () -> service.get(query(null, FROM, TO, Set.of(0L), Set.of())), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("거절 조건은 구간 안 배정을 한 번이라도 거절한 작업을 담아, 거절 뒤 다시 배정받아 완료한 작업도 통계의 거절 수와 같이 최신 상태로 담는다")
    void rejectedFilterMatchesRejectedStatistic() {
        fixture.givenManager();
        Work rejectedThenCompleted = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절 뒤 완료", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(1)));
        rejectedThenCompleted.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        rejectedThenCompleted.assign(new WorkSchedule(TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        rejectedThenCompleted.accept(NOW);
        rejectedThenCompleted.start(NOW);
        rejectedThenCompleted.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId id = fixture.workRepository.store(rejectedThenCompleted);

        WorkHistoryInfo rejected =
                service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of(WorkHistoryStatus.REJECTED)));

        assertThat(rejected.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId, WorkHistoryInfo.AssignedWork::status)
                .containsExactly(tuple(id.value(), WorkStatus.COMPLETED));
        assertThat(rejected.statistics().rejected()).isEqualTo(rejected.works().size());
    }

    @Test
    @DisplayName("기사 본인의 거절 조건은 거절 수 0과 같이 아무것도 담지 않는다(거절 뒤 다시 배정받아 완료한 작업도)")
    void technicianRejectedFilterIsEmpty() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work rejectedThenCompleted = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "거절 뒤 완료", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, at(1)));
        rejectedThenCompleted.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        rejectedThenCompleted.assign(new WorkSchedule(TECHNICIAN_ID, at(2), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        rejectedThenCompleted.accept(NOW);
        rejectedThenCompleted.start(NOW);
        rejectedThenCompleted.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId id = fixture.workRepository.store(rejectedThenCompleted);

        WorkHistoryInfo rejected = service.get(query(null, FROM, TO, Set.of(), Set.of(WorkHistoryStatus.REJECTED)));

        assertThat(rejected.works()).isEmpty();
        assertThat(rejected.statistics().rejected()).isZero();
        assertThat(service.get(query(null, FROM, TO)).works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(id.value());
    }

    @Test
    @DisplayName("기사 본인에게는 구간 밖으로 일정이 옮겨져 완료된 작업을 옮겨진 일정의 구간에서만 담는다")
    void technicianSeesCompletedWorkInPeriodOfFinalSchedule() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Instant later = TO.plus(Duration.ofHours(2));
        Work movedOut =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "옮겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN));
        movedOut.reschedule(later, TWO_HOURS, NOW, SETUP_MANAGER_ID);
        movedOut.start(NOW);
        movedOut.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId movedOutId = fixture.workRepository.store(movedOut);

        assertThat(service.get(query(null, FROM, TO)).works()).isEmpty();
        assertThat(service.get(query(null, TO, TO.plus(Duration.ofDays(1)))).works())
                .extracting(WorkHistoryInfo.AssignedWork::workId, WorkHistoryInfo.AssignedWork::assignmentNumber)
                .containsExactly(tuple(movedOutId.value(), 2));
    }

    @Test
    @DisplayName("구간 밖의 배정은 마지막 배정·거절 수에 넣지 않는다")
    void ignoresAssignmentsOutsidePeriod() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TO.plus(TWO_HOURS), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        fixture.workRepository.store(work);

        WorkHistoryInfo history = service.get(query(TECHNICIAN_ID.value(), FROM, TO));

        assertThat(history.works())
                .extracting(WorkHistoryInfo.AssignedWork::assignmentNumber, WorkHistoryInfo.AssignedWork::result)
                .containsExactly(tuple(1, AssignmentResult.REJECTED));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(1, 0, 1, 0));
        assertThat(service.get(query(TECHNICIAN_ID.value(), TO, TO.plus(Duration.ofDays(1))))
                        .statistics())
                .isEqualTo(new WorkHistoryInfo.Statistics(1, 0, 0, 0));
    }

    @Test
    @DisplayName("구간 밖으로 일정이 옮겨졌거나 다시 배정된 작업의 구간 안 배정은 지금 담당이 아니고, 완료는 그 배정이 속한 구간에서 한 번만 센다")
    void marksCurrentOnlyForLatestAssignment() {
        fixture.givenManager();
        Instant later = TO.plus(Duration.ofHours(2));
        Work movedOut =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "옮겨진 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN));
        movedOut.reschedule(later, TWO_HOURS, NOW, SETUP_MANAGER_ID);
        movedOut.start(NOW);
        movedOut.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        WorkId movedOutId = fixture.workRepository.store(movedOut);
        Work returned =
                fixture.stored(fixture.givenWork(ORGANIZATION_ID, "돌아온 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, at(1)));
        returned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, at(1), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        returned.reassign(new WorkSchedule(TECHNICIAN_ID, later, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId returnedId = fixture.workRepository.store(returned);

        WorkHistoryInfo inPeriod = service.get(query(TECHNICIAN_ID.value(), FROM, TO));
        WorkHistoryInfo nextPeriod = service.get(query(TECHNICIAN_ID.value(), TO, TO.plus(Duration.ofDays(1))));

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
    @DisplayName("구간 경계에 걸친 일정은 두 구간 목록에 모두 나오지만 완료·거절 수와 완료·거절 조건은 시작이 속한 구간에서만 센다")
    void countsBoundaryWorkOnlyInStartPeriod() {
        fixture.givenManager();
        Instant crossingStart = TO.minus(Duration.ofHours(1));
        WorkId crossing =
                fixture.givenWork(ORGANIZATION_ID, "밤샘 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, crossingStart);
        Work rejected = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "밤샘 거절", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, crossingStart));
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);

        WorkHistoryInfo startPeriod = service.get(query(TECHNICIAN_ID.value(), FROM, TO));
        WorkHistoryInfo endPeriod = service.get(query(TECHNICIAN_ID.value(), TO, TO.plus(Duration.ofDays(1))));

        assertThat(startPeriod.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value(), crossing.value());
        assertThat(endPeriod.works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value(), crossing.value());
        assertThat(startPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 1, 1, 0));
        assertThat(endPeriod.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(2, 0, 0, 0));
        assertThat(service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of(WorkHistoryStatus.REJECTED)))
                        .works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(rejectedId.value());
        assertThat(service.get(query(TECHNICIAN_ID.value(), FROM, TO, Set.of(), Set.of(WorkHistoryStatus.COMPLETED)))
                        .works())
                .extracting(WorkHistoryInfo.AssignedWork::workId)
                .containsExactly(crossing.value());
        assertThat(service.get(query(
                                TECHNICIAN_ID.value(),
                                TO,
                                TO.plus(Duration.ofDays(1)),
                                Set.of(),
                                Set.of(WorkHistoryStatus.COMPLETED)))
                        .works())
                .isEmpty();
        assertThat(service.get(query(
                                TECHNICIAN_ID.value(),
                                TO,
                                TO.plus(Duration.ofDays(1)),
                                Set.of(),
                                Set.of(WorkHistoryStatus.REJECTED)))
                        .works())
                .isEmpty();
    }

    @Test
    @DisplayName("거절한 뒤 같은 구간에서 다시 배정받아 완료한 작업은 완료와 거절에 함께 센다")
    void countsRejectedThenCompletedWorkInBoth() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        work.assign(new WorkSchedule(TECHNICIAN_ID, at(3), TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.accept(NOW);
        work.start(NOW);
        work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW);
        fixture.workRepository.store(work);

        WorkHistoryInfo history = service.get(query(TECHNICIAN_ID.value(), FROM, TO));

        assertThat(history.works())
                .extracting(WorkHistoryInfo.AssignedWork::assignmentNumber, WorkHistoryInfo.AssignedWork::current)
                .containsExactly(tuple(2, true));
        assertThat(history.statistics()).isEqualTo(new WorkHistoryInfo.Statistics(1, 1, 1, 0));
    }

    @Test
    @DisplayName("배정 시작시각이 같으면 작업 식별자 내림차순이다")
    void ordersSameStartByIdDescending() {
        fixture.givenManager();
        WorkId first = fixture.givenWork(ORGANIZATION_ID, "첫 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId second = fixture.givenWork(ORGANIZATION_ID, "둘째 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        assertThat(service.get(query(TECHNICIAN_ID.value(), FROM, TO)).works())
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

    private static GetWorkHistoryQuery query(
            Long technicianId, Instant from, Instant to, Set<Long> workTypeIds, Set<WorkHistoryStatus> statuses) {
        return new GetWorkHistoryQuery(
                ACCOUNT_ID, ORGANIZATION_ID.value(), technicianId, from, to, workTypeIds, statuses);
    }
}
