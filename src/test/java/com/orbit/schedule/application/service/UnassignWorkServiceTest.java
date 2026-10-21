package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("배정 해제")
class UnassignWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final UnassignWorkService service =
            new UnassignWorkService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("수락대기 작업을 해제하면 대기 중이던 배정을 지금 시각으로 회수하고 대기함으로 돌린다")
    void unassignsPendingWork() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        service.unassign(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
            assertThat(history.decidedAt()).contains(NOW);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.UNASSIGNED));
        });
    }

    @Test
    @DisplayName("수락된 작업을 해제하면 수락 결과는 그대로 두고 해제 시각·처리자를 남긴 채 대기함으로 돌린다")
    void unassignsAcceptedWork() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        service.unassign(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(ACCEPTED_AT);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.UNASSIGNED));
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "IN_PROGRESS", "COMPLETED", "CANCELED"})
    @DisplayName("수락대기·수락됨이 아니면 해제할 수 없다")
    void rejectsUnassignableStatus(WorkStatus status) {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(() -> service.unassign(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("해제 시각이 대기 중인 배정의 배정 시각보다 앞서면 입력 오류다")
    void rejectsUnassignmentBeforeAssignment() {
        fixture.givenManager(ActorRole.OWNER);
        Work work = Work.register(ORGANIZATION_ID, "나중에 배정된 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW.plus(Duration.ofHours(1)), MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.unassign(command(id.value())), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("해제 시각이 수락 시각보다 앞서면 입력 오류다")
    void rejectsUnassignmentBeforeAcceptance() {
        fixture.givenManager(ActorRole.OWNER);
        Work work = Work.register(ORGANIZATION_ID, "나중에 수락된 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, MANAGER_ID);
        work.accept(NOW.plus(Duration.ofHours(1)));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.unassign(command(id.value())), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("요청자가 본 버전이 없으면 입력 오류, 지금 버전과 다르면 상태보다 먼저 변경된 정보 오류다")
    void checksExpectedVersionBeforeState() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId backlog = fixture.givenWork(WorkStatus.REGISTERED);

        fixture.assertRejected(
                () -> service.unassign(
                        new UnassignWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), backlog.value(), null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.unassign(
                        new UnassignWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), backlog.value(), 1L)),
                ScheduleErrorCode.WORK_CHANGED);
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("저장 자리에서 다른 트랜잭션이 먼저 바꾼 것이 드러나면 변경된 정보 오류다")
    void rejectsConcurrentChangeAtSave() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);
        fixture.workRepository.failNextSaveWithConcurrentChange();

        fixture.assertRejected(() -> service.unassign(command(id.value())), ScheduleErrorCode.WORK_CHANGED);
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    private static UnassignWorkCommand command(long workId) {
        return new UnassignWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 0L);
    }
}
