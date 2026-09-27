package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 취소")
class CancelWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final CancelWorkService service =
            new CancelWorkService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"PENDING_ACCEPTANCE", "ACCEPTED", "IN_PROGRESS"})
    @DisplayName("배정이 있는 작업을 취소하면 지금 시각·처리자·사유를 작업에 남기고 그 배정도 같은 시각·처리자의 취소로 끝낸다")
    void cancelsAssignedWork(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        service.cancel(command(id.value(), "고객 요청"));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.CANCELLED);
        assertThat(saved.cancellation()).contains(new Cancellation(NOW, MANAGER_ID, "고객 요청"));
        assertThat(saved.assignmentHistory().getLast().ending())
                .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.CANCELLED));
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @Test
    @DisplayName("대기함 작업을 취소해도 처리자와 사유를 남긴다")
    void cancelsBacklogWork() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        service.cancel(command(id.value(), "중복 등록"));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.CANCELLED);
        assertThat(saved.cancellation()).contains(new Cancellation(NOW, MANAGER_ID, "중복 등록"));
        assertThat(saved.assignmentHistory()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"COMPLETED", "CANCELLED"})
    @DisplayName("완료됐거나 이미 취소된 작업은 취소할 수 없다")
    void rejectsTerminalWork(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(
                () -> service.cancel(command(id.value(), "고객 요청")), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("사유가 없거나 비었거나 255자를 넘으면, 완료된 작업이라도 상태보다 먼저 입력 오류다")
    void rejectsInvalidReasonBeforeState() {
        fixture.givenManager();
        WorkId registered = fixture.givenWork(WorkStatus.REGISTERED);
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        for (WorkId id : new WorkId[] {registered, completed}) {
            for (String reason : new String[] {null, " ", "가".repeat(256)}) {
                fixture.assertRejected(
                        () -> service.cancel(command(id.value(), reason)), ScheduleErrorCode.INVALID_WORK_INPUT);
            }
        }
    }

    @Test
    @DisplayName("취소 시각이 배정 이력의 마지막 시각보다 앞서면(서버 간 시계 차이) 입력 오류이고, 취소할 수 없는 상태면 그보다 먼저 상태 오류다")
    void rejectsCancellationBeforeLatestHistory() {
        fixture.givenManager();
        Work rejectedLater = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        rejectedLater.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), NOW.plusSeconds(60));
        WorkId backlog = fixture.workRepository.store(rejectedLater);
        Work reassignedLater = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        reassignedLater.reassign(
                new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), NOW.plusSeconds(60), SETUP_MANAGER_ID);
        WorkId pending = fixture.workRepository.store(reassignedLater);

        Work completedLater = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        completedLater.accept(NOW.plusSeconds(60));
        completedLater.start(NOW.plusSeconds(60));
        completedLater.submitCompletionReport(
                new CompletionReport(null, null, null, null, null, null), NOW.plusSeconds(60));
        WorkId completed = fixture.workRepository.store(completedLater);

        for (WorkId id : new WorkId[] {backlog, pending}) {
            fixture.assertRejected(
                    () -> service.cancel(command(id.value(), "고객 요청")), ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        fixture.assertRejected(
                () -> service.cancel(command(completed.value(), "고객 요청")), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("작업중에 취소하면 시작 시각을 그대로 남긴다")
    void keepsStartedAtOfInProgressWork() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

        service.cancel(command(id.value(), "고객 부재"));

        assertThat(fixture.singleSaved().startedAt()).contains(ACCEPTED_AT);
    }

    private static CancelWorkCommand command(long workId, String reason) {
        return new CancelWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, reason);
    }
}
