package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("배정 거절")
class RejectWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final RejectWorkService service =
            new RejectWorkService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("담당 기사가 최신 배정을 사유와 함께 거절하면 지금 시각으로 거절을 기록하고 대기함으로 돌린다")
    void rejectsCurrentAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        service.reject(command(id.value(), 1, RejectionReason.LOCATION_TOO_FAR, null));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
            assertThat(history.rejection()).contains(new Rejection(RejectionReason.LOCATION_TOO_FAR, null));
            assertThat(history.decidedAt()).contains(NOW);
            assertThat(history.ending()).isEmpty();
        });
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @Test
    @DisplayName("기타 사유로 거절하면 메모를 함께 남긴다")
    void keepsNoteForOtherReason() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        service.reject(command(id.value(), 1, RejectionReason.OTHER, "차량 고장"));

        assertThat(fixture.singleSaved().assignmentHistory().getLast().rejection())
                .contains(new Rejection(RejectionReason.OTHER, "차량 고장"));
    }

    @Test
    @DisplayName("같은 사유·메모로 이미 거절한 배정에 다시 보내면 아무것도 바꾸지 않고 성공한다")
    void treatsRepeatedRejectionAsSuccess() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = rejectedWork(new Rejection(RejectionReason.OTHER, "차량 고장"));

        service.reject(command(id.value(), 1, RejectionReason.OTHER, "차량 고장"));

        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("다른 사유나 다른 메모로 이미 거절했거나 수락한 배정은 거절할 수 없다")
    void rejectsConflictingResponse() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId rejected = rejectedWork(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null));
        WorkId rejectedWithNote = rejectedWork(new Rejection(RejectionReason.OTHER, "차량 고장"));
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);

        fixture.assertRejected(
                () -> service.reject(command(rejected.value(), 1, RejectionReason.LOCATION_TOO_FAR, null)),
                ScheduleErrorCode.INVALID_WORK_STATE);
        fixture.assertRejected(
                () -> service.reject(command(rejectedWithNote.value(), 1, RejectionReason.OTHER, "다른 메모")),
                ScheduleErrorCode.INVALID_WORK_STATE);
        fixture.assertRejected(
                () -> service.reject(command(accepted.value(), 1, RejectionReason.LOCATION_TOO_FAR, null)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("거절한 뒤 관리자가 대기함에서 작업을 취소해도, 거절한 배정은 그대로이므로 같은 거절 재전송은 성공한다")
    void treatsRepeatedRejectionAsSuccessAfterBacklogCancellation() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(rejectedWork(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null)));
        work.cancel(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        service.reject(command(id.value(), 1, RejectionReason.SCHEDULE_CONFLICT, null));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.CANCELLED);
    }

    @Test
    @DisplayName("사유가 없거나, 기타인데 메모가 없거나, 기타가 아닌데 메모가 있거나, 메모가 255자를 넘으면 입력 오류다")
    void rejectsInvalidRejection() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        fixture.assertRejected(
                () -> service.reject(command(id.value(), 1, null, null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reject(command(id.value(), 1, RejectionReason.OTHER, " ")),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reject(command(id.value(), 1, RejectionReason.SCOPE_MISMATCH, "메모")),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reject(command(id.value(), 1, RejectionReason.OTHER, "가".repeat(256))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("거절 입력이 잘못되면 담당 여부·최신 여부·상태보다 먼저 입력 오류다")
    void checksRejectionBeforeOwnershipAndState() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work reassigned = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        reassigned.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        WorkId reassignedId = fixture.workRepository.store(reassigned);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);

        fixture.assertRejected(
                () -> service.reject(command(reassignedId.value(), 2, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reject(command(reassignedId.value(), 1, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reject(command(accepted.value(), 1, null, null)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("응답 시각이 배정 시각보다 앞서면(서버 간 시계 차이) 입력 오류다")
    void rejectsResponseBeforeAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = rescheduledAfterNow();

        fixture.assertRejected(
                () -> service.reject(command(id.value(), 2, RejectionReason.SCHEDULE_CONFLICT, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("응답할 수 없는 상태면 응답 시각이 배정 시각보다 앞서도 상태 오류다")
    void checksStateBeforeResponseTime() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(rescheduledAfterNow());
        work.unassign(NOW.plusSeconds(120), SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(
                () -> service.reject(command(id.value(), 2, RejectionReason.SCHEDULE_CONFLICT, null)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    /** 수락대기 작업을 고정 시계({@link ScheduleServiceFixture#NOW})보다 늦은 시각에 같은 기사의 다른 시간으로 바꿔 둔다. 새 배정은 2번이다. */
    private WorkId rescheduledAfterNow() {
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reschedule(TEN.plusSeconds(3_600), TWO_HOURS, NOW.plusSeconds(60), SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    private WorkId rejectedWork(Rejection rejection) {
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reject(rejection, ACCEPTED_AT);
        return fixture.workRepository.store(work);
    }

    private static RejectWorkCommand command(long workId, int number, RejectionReason reason, String note) {
        return new RejectWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, number, reason, note);
    }
}
