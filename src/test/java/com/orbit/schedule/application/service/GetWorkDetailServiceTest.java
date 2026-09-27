package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ASSIGNED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.AssignmentView;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 상세 조회")
class GetWorkDetailServiceTest {

    private static final MembershipId OWNER_ID = new MembershipId(13L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetWorkDetailService service =
            new GetWorkDetailService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("관리자는 고객·결제·일정·배정 이력·진행 기록·완료보고·강제 변경 기록을 모두 본다")
    void managerSeesEverything() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.COMPLETED));
        work.correctStatus(WorkStatus.IN_PROGRESS, NOW, OWNER_ID, "사진 누락");
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.workId()).isEqualTo(id.value());
        assertThat(detail.name()).isEqualTo("대상 작업");
        assertThat(detail.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(detail.workTypeId()).isEqualTo(2L);
        assertThat(detail.customer().name()).isEqualTo("홍길동");
        assertThat(detail.payment().fee()).isEqualTo(150_000L);
        assertThat(detail.schedule().technicianId()).isEqualTo(TECHNICIAN_ID.value());
        assertThat(detail.schedule().endTime()).isEqualTo(TEN.plus(TWO_HOURS));
        assertThat(detail.assignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.assignmentNumber()).isEqualTo(1);
            assertThat(assignment.assignedAt()).isEqualTo(ASSIGNED_AT);
            assertThat(assignment.assignedBy()).isEqualTo(SETUP_MANAGER_ID.value());
            assertThat(assignment.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(assignment.decidedAt()).isEqualTo(ACCEPTED_AT);
        });
        assertThat(detail.startedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(detail.completedAt()).isNull();
        assertThat(detail.completionReport()).isNull();
        assertThat(detail.statusCorrections()).singleElement().satisfies(correction -> {
            assertThat(correction.from()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(correction.correctedBy()).isEqualTo(OWNER_ID.value());
            assertThat(correction.reason()).isEqualTo("사진 누락");
            assertThat(correction.retiredReport()).isNotNull();
            assertThat(correction.retiredCompletedAt()).isEqualTo(ACCEPTED_AT);
        });
    }

    @Test
    @DisplayName("지금 담당인 기사는 고객·일정·진행 기록·완료보고를 보지만 강제 변경 기록은 받지 않는다")
    void currentTechnicianSeesTheirWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.COMPLETED));
        work.correctStatus(WorkStatus.IN_PROGRESS, NOW, OWNER_ID, "사진 누락");
        work.submitCompletionReport(new CompletionReport(null, null, null, "다시 보고", null, null), NOW);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.customer()).isNotNull();
        assertThat(detail.payment()).isNotNull();
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.startedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(detail.completedAt()).isEqualTo(NOW);
        assertThat(detail.completionReport().workNote()).isEqualTo("다시 보고");
        assertThat(detail.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("다른 기사로 바뀐 작업의 이전 기사는 작업명·상태와 자기 배정만 원래 순번으로 본다")
    void formerTechnicianSeesOnlyTheirAssignments() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.reassign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.name()).isEqualTo("대상 작업");
        assertThat(detail.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(detail.assignments())
                .extracting(AssignmentView::assignmentNumber, AssignmentView::endReason)
                .containsExactly(tuple(1, AssignmentEndReason.REASSIGNED), tuple(3, AssignmentEndReason.REASSIGNED));
        assertThat(detail.customer()).isNull();
        assertThat(detail.payment()).isNull();
        assertThat(detail.schedule()).isNull();
        assertThat(detail.startedAt()).isNull();
        assertThat(detail.completionReport()).isNull();
        assertThat(detail.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("한 번도 배정받지 않은 기사에게는 작업의 존재를 숨긴다")
    void hidesWorkFromNeverAssignedTechnician() {
        fixture.givenTechnician(OTHER_TECHNICIAN_ID);
        WorkId assigned = fixture.givenWork(WorkStatus.ACCEPTED);
        WorkId backlog = fixture.givenWork(WorkStatus.REGISTERED);

        fixture.assertRejected(() -> service.get(query(assigned.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        fixture.assertRejected(() -> service.get(query(backlog.value())), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("예정 종료시각이 지났는데 끝나지 않은 작업은 지연으로, 끝난 작업은 지연이 아닌 것으로 표시한다")
    void marksDelay() {
        fixture.givenManager(ActorRole.OWNER);
        Instant pastStart = NOW.minus(TWO_HOURS);
        WorkId late = fixture.givenWork(ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart);
        WorkId done = fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, pastStart);
        WorkId running = fixture.givenWork(
                ORGANIZATION_ID,
                "진행 중 작업",
                WorkStatus.IN_PROGRESS,
                TECHNICIAN_ID,
                pastStart.plus(Duration.ofMinutes(1)));

        assertThat(service.get(query(late.value())).delayed()).isTrue();
        assertThat(service.get(query(done.value())).delayed()).isFalse();
        assertThat(service.get(query(running.value())).delayed()).isFalse();
    }

    @Test
    @DisplayName("계정 식별자가 없으면 멈추고, 조직 식별자 → 구성원 → 작업 식별자 → 작업 조회 순서로 확인한다")
    void checksCommonOrder() {
        fixture.givenManager();
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);

        assertThatThrownBy(() -> service.get(new GetWorkDetailQuery(null, ORGANIZATION_ID.value(), 1L)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.assertRejected(
                () -> service.get(new GetWorkDetailQuery(ACCOUNT_ID, 0L, 1L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.get(new GetWorkDetailQuery(ACCOUNT_ID, OTHER_ORGANIZATION_ID.value(), 1L)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        fixture.assertRejected(() -> service.get(query(null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.get(query(UNKNOWN_WORK_ID)), ScheduleErrorCode.WORK_NOT_FOUND);
        fixture.assertRejected(
                () -> service.get(query(otherOrganizationWork.value())), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    private static GetWorkDetailQuery query(Long workId) {
        return new GetWorkDetailQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
