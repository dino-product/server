package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("배정 수락")
class AcceptWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final AcceptWorkService service =
            new AcceptWorkService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("담당 기사가 화면에서 본 최신 배정을 수락하면 지금 시각으로 수락을 기록하고 일정은 그대로 둔다")
    void acceptsCurrentAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        service.accept(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(NOW);
            assertThat(history.ending()).isEmpty();
        });
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"ACCEPTED", "IN_PROGRESS", "COMPLETED"})
    @DisplayName("이미 수락해 그대로인 배정에 수락을 다시 보내면 아무것도 바꾸지 않고 성공한다")
    void treatsRepeatedAcceptanceAsSuccess(WorkStatus status) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(status);

        service.accept(command(id.value()));

        assertThat(fixture.workRepository.saved()).isEmpty();
        Work stored = fixture.stored(id);
        assertThat(stored.status()).isEqualTo(status);
        assertThat(stored.assignmentHistory().getLast().decidedAt()).contains(ACCEPTED_AT);
    }

    @Test
    @DisplayName("이미 거절한 배정은 수락할 수 없다")
    void rejectsAcceptingRejectedAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.accept(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    private static AcceptWorkCommand command(long workId) {
        return new AcceptWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1);
    }
}
