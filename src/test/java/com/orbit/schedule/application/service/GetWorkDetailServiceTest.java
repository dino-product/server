package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ASSIGNED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.PhotoInfo;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/** 공통 오류 순서는 {@link WorkQueryRulesTest}가 다룬다. */
@DisplayName("작업 상세 조회")
class GetWorkDetailServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final GetWorkDetailService service = new GetWorkDetailService(
            fixture.actorPort,
            fixture.workRepository,
            photoId -> "url/" + photoId,
            fixture.technicianProfiles,
            fixture.staffProfiles,
            fixture.workTypeLabels,
            fixture.clock);

    @Test
    @DisplayName("관리자는 작업유형·등록자·등록 시각·고객·결제·일정(기사 이름·기사유형)·배정 이력·진행 기록·완료보고(사진 주소 포함)를 모두 본다")
    void managerSeesEverything() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.submitCompletionReport(new CompletionReport(List.of("before-1"), null, null, "첫 보고", null, null), NOW);
        WorkId id = fixture.workRepository.store(work);

        fixture.technicianProfiles.given(
                ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김민준", new TypeLabel(7L, "배관 전문", null, false)));
        fixture.staffProfiles.given(ORGANIZATION_ID, new StaffProfile(REGISTRAR_ID, "김운영", false, null));
        fixture.workTypeLabels.given(ORGANIZATION_ID, new TypeLabel(WORK_TYPE_ID.value(), "배관·설비", "#3366FF", false));

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.workId()).isEqualTo(id.value());
        assertThat(detail.name()).isEqualTo("대상 작업");
        assertThat(detail.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(detail.workType()).isEqualTo(new LabelInfo(WORK_TYPE_ID.value(), "배관·설비", "#3366FF"));
        assertThat(detail.registrar()).isEqualTo(new StaffInfo(REGISTRAR_ID.value(), "김운영", false, null));
        assertThat(detail.registeredAt()).isEqualTo(REGISTERED_AT);
        assertThat(detail.customer().name()).isEqualTo("홍길동");
        assertThat(detail.payment().fee()).isEqualTo(150_000L);
        assertThat(detail.schedule().technician())
                .isEqualTo(new TechnicianInfo(TECHNICIAN_ID.value(), "김민준", new LabelInfo(7L, "배관 전문", null)));
        assertThat(detail.schedule().endTime()).isEqualTo(TEN.plus(TWO_HOURS));
        assertThat(detail.assignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.assignmentNumber()).isEqualTo(1);
            assertThat(assignment.schedule().technician().name()).isEqualTo("김민준");
            assertThat(assignment.assignedAt()).isEqualTo(ASSIGNED_AT);
            assertThat(assignment.assignedBy()).isEqualTo(SETUP_MANAGER_ID.value());
            assertThat(assignment.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(assignment.decidedAt()).isEqualTo(ACCEPTED_AT);
        });
        assertThat(detail.startedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(detail.completedAt()).isEqualTo(NOW);
        assertThat(detail.completionReport().workNote()).isEqualTo("첫 보고");
        assertThat(detail.completionReport().beforePhotos()).containsExactly(new PhotoInfo("before-1", "url/before-1"));
    }

    @Test
    @DisplayName("관리자는 거절 사유·메모와 취소 기록·취소 종료를 본다")
    void managerSeesRejectionAndCancellation() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), ACCEPTED_AT);
        work.assign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        work.cancel(NOW.minusSeconds(60), SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.CANCELED);
        assertThat(detail.cancellation())
                .isEqualTo(new WorkDetailInfo.Cancellation(
                        NOW.minusSeconds(60), SETUP_MANAGER_ID.value(), CancellationReason.CUSTOMER_REQUEST, null));
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
    }

    @Test
    @DisplayName("기사의 탈퇴로 끝난 배정은 종료 방식·시각을 보이고 처리자는 비운다")
    void showsWithdrawalEndingWithoutProcessor() {
        fixture.givenManager();
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.releaseForWithdrawal(NOW);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(detail.assignments())
                .extracting(
                        WorkDetailInfo.Assignment::result,
                        WorkDetailInfo.Assignment::endReason,
                        WorkDetailInfo.Assignment::endedAt,
                        WorkDetailInfo.Assignment::endedBy)
                .containsExactly(tuple(AssignmentResult.ACCEPTED, AssignmentEndReason.ACCOUNT_WITHDRAWN, NOW, null));
    }

    @Test
    @DisplayName("관리자는 대기함 작업이 등록 때 일부만 입력해 둔 담당기사·시간을 본다")
    void showsPlannedScheduleOfBacklogWork() {
        fixture.givenManager();
        WorkId id = fixture.workRepository.store(Work.register(
                ORGANIZATION_ID,
                "시간만 정한 작업",
                SETUP_MANAGER_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(null, TEN, TWO_HOURS)));

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.schedule()).isNull();
        assertThat(detail.plannedSchedule()).isEqualTo(new WorkDetailInfo.PlannedSchedule(null, TEN, TWO_HOURS));
        assertThat(detail.registeredAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    @DisplayName("지금 담당인 기사는 고객·일정·진행 기록·완료보고·현장 사진을 보지만 등록자는 받지 않는다")
    void currentTechnicianSeesTheirWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.replaceSitePhotos(List.of("site-1"));
        work.submitCompletionReport(new CompletionReport(null, List.of("after-1"), null, "다시 보고", null, null), NOW);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.workType()).isEqualTo(new LabelInfo(WORK_TYPE_ID.value(), null, null));
        assertThat(detail.registeredAt()).isEqualTo(REGISTERED_AT);
        assertThat(detail.customer()).isNotNull();
        assertThat(detail.payment()).isNotNull();
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.startedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(detail.completedAt()).isEqualTo(NOW);
        assertThat(detail.completionReport().workNote()).isEqualTo("다시 보고");
        assertThat(detail.completionReport().afterPhotos()).containsExactly(new PhotoInfo("after-1", "url/after-1"));
        assertThat(detail.registrar()).as("등록자는 관리자에게만 보인다").isNull();
        assertThat(fixture.staffProfiles.lookups())
                .as("기사에게 보이지 않는 등록자는 찾지 않는다")
                .isEmpty();
        assertThat(detail.sitePhotos()).containsExactly(new PhotoInfo("site-1", "url/site-1"));
    }

    @Test
    @DisplayName("관리자는 다른 기사로 넘어간 배정 이력의 기사 이름도 본다")
    void managerSeesNamesOfPastAssignees() {
        fixture.givenManager();
        fixture.technicianProfiles.given(ORGANIZATION_ID, new TechnicianProfile(TECHNICIAN_ID, "김민준", null));
        fixture.technicianProfiles.given(ORGANIZATION_ID, new TechnicianProfile(OTHER_TECHNICIAN_ID, "이철호", null));
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.assignments())
                .extracting(assignment -> assignment.schedule().technician().name())
                .containsExactly("김민준", "이철호");
        assertThat(detail.schedule().technician().name()).isEqualTo("이철호");
    }

    @Test
    @DisplayName("삭제된 작업유형은 시작 전 작업에는 없는 것으로, 작업중 작업에는 그대로 보인다")
    void hidesDeletedWorkTypeBeforeStart() {
        fixture.givenManager();
        fixture.workTypeLabels.given(ORGANIZATION_ID, new TypeLabel(WORK_TYPE_ID.value(), "설치", null, true));
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);
        WorkId inProgress = fixture.givenWork(WorkStatus.IN_PROGRESS);

        assertThat(service.get(query(accepted.value())).workType()).isNull();
        assertThat(service.get(query(inProgress.value())).workType())
                .isEqualTo(new LabelInfo(WORK_TYPE_ID.value(), "설치", null));
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
    @DisplayName("배정 중 취소된 작업은 취소 당시 담당 기사에게 취소 기록과 함께 보인다")
    void technicianOfCancelledAssignmentSeesCancellation() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.CANCELED);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.CANCELED);
        assertThat(detail.cancellation())
                .isEqualTo(new WorkDetailInfo.Cancellation(
                        ACCEPTED_AT, SETUP_MANAGER_ID.value(), CancellationReason.CUSTOMER_REQUEST, null));
        assertThat(detail.customer()).isNotNull();
        assertThat(detail.schedule()).isNotNull();
    }

    @Test
    @DisplayName("다른 기사로 재배정됐거나 거절해 대기함으로 돌아간 작업은 이전 기사가 볼 수 없어 찾을 수 없음이다")
    void hidesWorkFromFormerTechnician() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        Work rejected = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId rejectedId = fixture.workRepository.store(rejected);

        fixture.assertRejected(() -> service.get(query(reassignedId.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        fixture.assertRejected(() -> service.get(query(rejectedId.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        assertThat(fixture.technicianProfiles.lookups())
                .as("볼 수 없는 작업의 표시 정보는 찾지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("다른 기사를 거쳐 다시 배정받은 기사는 작업을 다시 보고, 지금 배정 하나만 원래 순번으로 받는다")
    void technicianSeesWorkAgainAfterReassignedBack() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        work.reassign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        assertThat(detail.customer()).isNotNull();
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.assignments())
                .extracting(WorkDetailInfo.Assignment::assignmentNumber, WorkDetailInfo.Assignment::endReason)
                .containsExactly(tuple(3, null));
    }

    @Test
    @DisplayName("거절한 작업을 같은 기사에게 다시 배정하면 그 기사에게 새 배정으로 다시 보이고 지난 거절은 보이지 않는다")
    void technicianSeesRejectedWorkAgainAfterNewAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        WorkDetailInfo detail = service.get(query(id.value()));

        assertThat(detail.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.assignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.assignmentNumber()).isEqualTo(2);
            assertThat(assignment.rejectionReason()).isNull();
        });
    }

    @Test
    @DisplayName("예정 종료시각과 같거나 지났는데 끝나지 않았거나 그 뒤에 완료한 작업은 지연으로, 제때 끝난 작업이나 아직 시간이 남은 작업은 지연이 아닌 것으로 표시한다")
    void marksDelay() {
        fixture.givenManager(ActorRole.OWNER);
        Instant pastStart = NOW.minus(TWO_HOURS);
        WorkId late = fixture.givenWork(ORGANIZATION_ID, "늦은 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart);
        WorkId done = fixture.givenWork(ORGANIZATION_ID, "끝난 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, pastStart);
        // 완료(ACCEPTED_AT = NOW - 1.5시간)가 예정 종료(NOW - 2시간)보다 늦다.
        WorkId doneLate = fixture.givenWork(
                ORGANIZATION_ID, "늦게 끝난 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, NOW.minus(Duration.ofHours(4)));
        WorkId running = fixture.givenWork(
                ORGANIZATION_ID, "진행 중 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, pastStart.plusSeconds(60));

        assertThat(service.get(query(late.value())).delayed()).isTrue();
        assertThat(service.get(query(done.value())).delayed()).isFalse();
        assertThat(service.get(query(doneLate.value())).delayed()).isTrue();
        assertThat(service.get(query(running.value())).delayed()).isFalse();
    }

    private static GetWorkDetailQuery query(Long workId) {
        return new GetWorkDetailQuery(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
    }
}
