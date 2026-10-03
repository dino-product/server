package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ASSIGNED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.domain.ActorRole;
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

/** 공통 오류 순서는 {@link WorkQueryRulesTest}가 다룬다. */
@DisplayName("작업 상세 조회")
class GetWorkDetailServiceTest {

    private static final MembershipId OWNER_ID = new MembershipId(13L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetWorkDetailService service = new GetWorkDetailService(
            fixture.actorPort, fixture.workRepository, photoId -> "url/" + photoId, fixture.clock);

    @Test
    @DisplayName("관리자는 고객·결제·일정·배정 이력·진행 기록과 완료를 되돌린 강제 변경 기록(치운 보고의 사진 주소 포함)을 모두 본다")
    void managerSeesEverything() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.submitCompletionReport(new CompletionReport(List.of("before-1"), null, null, "첫 보고", null, null), NOW);
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
            assertThat(correction.retiredReport().workNote()).isEqualTo("첫 보고");
            assertThat(correction.retiredReport().beforePhotos())
                    .containsExactly(new CompletionReportInfo.Photo("before-1", "url/before-1"));
            assertThat(correction.retiredCompletedAt()).isEqualTo(NOW);
            assertThat(correction.restoredAssignmentNumber()).isNull();
        });
    }

    @Test
    @DisplayName("관리자는 거절 사유·메모, 취소 종료, 되돌린 취소 기록과 되돌린 배정 순번을 본다")
    void managerSeesRejectionCancellationAndRestoration() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        work.assign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        work.cancel(NOW.minusSeconds(60), SETUP_MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, NOW, OWNER_ID, "잘못 취소");
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(detail.schedule()).isNull();
        assertThat(detail.cancellation()).isNull();
        assertThat(detail.assignments())
                .extracting(
                        WorkDetailInfo.Assignment::assignmentNumber,
                        WorkDetailInfo.Assignment::result,
                        WorkDetailInfo.Assignment::rejectionReason,
                        WorkDetailInfo.Assignment::rejectionNote,
                        WorkDetailInfo.Assignment::endReason,
                        WorkDetailInfo.Assignment::endedAt,
                        WorkDetailInfo.Assignment::endedBy)
                .containsExactly(
                        tuple(1, AssignmentResult.REJECTED, RejectionReason.OTHER, "장비 없음", null, null, null),
                        tuple(
                                2,
                                AssignmentResult.WITHDRAWN,
                                null,
                                null,
                                AssignmentEndReason.CANCELLED,
                                NOW.minusSeconds(60),
                                SETUP_MANAGER_ID.value()));
        assertThat(detail.statusCorrections()).singleElement().satisfies(correction -> {
            assertThat(correction.retiredCancellation())
                    .isEqualTo(
                            new WorkDetailInfo.Cancellation(NOW.minusSeconds(60), SETUP_MANAGER_ID.value(), "고객 요청"));
            assertThat(correction.restoredAssignmentNumber()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("지금 담당인 기사는 고객·일정·진행 기록·완료보고를 보지만 강제 변경 기록은 받지 않는다")
    void currentTechnicianSeesTheirWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.COMPLETED));
        work.correctStatus(WorkStatus.IN_PROGRESS, NOW, OWNER_ID, "사진 누락");
        work.submitCompletionReport(new CompletionReport(null, List.of("after-1"), null, "다시 보고", null, null), NOW);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.workTypeId()).isEqualTo(2L);
        assertThat(detail.customer()).isNotNull();
        assertThat(detail.payment()).isNotNull();
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.startedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(detail.completedAt()).isEqualTo(NOW);
        assertThat(detail.completionReport().workNote()).isEqualTo("다시 보고");
        assertThat(detail.completionReport().afterPhotos())
                .containsExactly(new CompletionReportInfo.Photo("after-1", "url/after-1"));
        assertThat(detail.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("지금 담당인 기사도 지연된 작업은 지연으로 본다")
    void currentTechnicianSeesDelay() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId late = fixture.givenWork(
                ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, NOW.minus(TWO_HOURS));

        assertThat(service.get(query(late.value())).delayed()).isTrue();
    }

    @Test
    @DisplayName("배정 중 취소된 작업의 담당 기사는 취소 기록을 본다")
    void currentTechnicianSeesCancellation() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.CANCELLED);

        assertThat(service.get(query(id.value())).cancellation())
                .isEqualTo(new WorkDetailInfo.Cancellation(ACCEPTED_AT, SETUP_MANAGER_ID.value(), "고객 요청"));
    }

    @Test
    @DisplayName("다른 기사로 바뀐 작업의 이전 기사는 작업명·상태와 자기 배정만 원래 순번으로 보고, 지연도 보지 않는다")
    void formerTechnicianSeesOnlyTheirAssignments() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Instant pastStart = NOW.minus(Duration.ofHours(3));
        Work work = fixture.stored(
                fixture.givenWork(ORGANIZATION_ID, "대상 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, pastStart));
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, pastStart, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.reassign(new WorkSchedule(TECHNICIAN_ID, pastStart, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, pastStart, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);
        assertThat(work.isDelayedAt(NOW)).isTrue();

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.name()).isEqualTo("대상 작업");
        assertThat(detail.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(detail.assignments())
                .extracting(WorkDetailInfo.Assignment::assignmentNumber, WorkDetailInfo.Assignment::endReason)
                .containsExactly(tuple(1, AssignmentEndReason.REASSIGNED), tuple(3, AssignmentEndReason.REASSIGNED));
        assertHidden(detail);
    }

    @Test
    @DisplayName("거절해 대기함으로 돌아간 작업의 기사는 작업명·상태와 자기 배정만 본다")
    void rejectingTechnicianSeesOnlyTheirAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(detail.assignments())
                .extracting(WorkDetailInfo.Assignment::assignmentNumber, WorkDetailInfo.Assignment::rejectionReason)
                .containsExactly(tuple(1, RejectionReason.SCHEDULE_CONFLICT));
        assertHidden(detail);
    }

    @Test
    @DisplayName("예정 종료시각과 같거나 지났는데 끝나지 않은 작업은 지연으로, 끝난 작업이나 아직 시간이 남은 작업은 지연이 아닌 것으로 표시한다")
    void marksDelay() {
        fixture.givenManager(ActorRole.OWNER);
        Instant pastStart = NOW.minus(TWO_HOURS);
        WorkId late = fixture.givenWork(ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart);
        WorkId done = fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, pastStart);
        WorkId running = fixture.givenWork(
                ORGANIZATION_ID, "진행 중 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart.plusSeconds(60));

        assertThat(service.get(query(late.value())).delayed()).isTrue();
        assertThat(service.get(query(done.value())).delayed()).isFalse();
        assertThat(service.get(query(running.value())).delayed()).isFalse();
    }

    private static void assertHidden(WorkDetailInfo detail) {
        assertThat(detail.delayed()).isFalse();
        assertThat(detail.workTypeId()).isNull();
        assertThat(detail.customer()).isNull();
        assertThat(detail.payment()).isNull();
        assertThat(detail.schedule()).isNull();
        assertThat(detail.startedAt()).isNull();
        assertThat(detail.completedAt()).isNull();
        assertThat(detail.cancellation()).isNull();
        assertThat(detail.completionReport()).isNull();
        assertThat(detail.statusCorrections()).isEmpty();
    }

    private static GetWorkDetailQuery query(Long workId) {
        return new GetWorkDetailQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
