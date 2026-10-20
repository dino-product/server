package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.WorkAccepted;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("배정 수락")
class AcceptWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final AcceptWorkService service = new AcceptWorkService(
            fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.events, fixture.clock);

    @Test
    @DisplayName(
            "담당 기사가 화면에서 본 최신 배정을 수락하면 지금 시각으로 수락을 기록하고 일정은 그대로 두며, 기사를 잠그되 다른 작업은 읽지 않고, " + "저장한 뒤 등록자에게 알릴 수락을 발행한다")
    void acceptsCurrentAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        service.accept(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(NOW);
            assertThat(history.ending()).isEmpty();
        });
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isZero();
        assertThat(fixture.events.published())
                .containsExactly(new WorkAccepted(
                        id.value(),
                        ORGANIZATION_ID.value(),
                        REGISTRAR_ID.value(),
                        TECHNICIAN_ID.value(),
                        "대상 작업",
                        NOW));
    }

    @Test
    @DisplayName("기사 잠금을 기다리는 동안 대기함 반환이 먼저 커밋되면 반환된 작업을 보고 찾을 수 없음이다")
    void seesReleaseCommittedWhileWaitingForLock() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);
        fixture.scheduleLock.whileWaiting(() -> {
            Work released = fixture.stored(id);
            released.unassign(NOW, SETUP_MANAGER_ID);
            fixture.workRepository.store(released);
        });

        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(id.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("기사 잠금을 기다리는 동안 같은 배정의 수락이 먼저 커밋되면 중복 전송으로 보고 바꾸지 않고 성공한다")
    void seesAcceptanceCommittedWhileWaitingForLock() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);
        Instant firstAcceptedAt = NOW.minusSeconds(1);
        fixture.scheduleLock.whileWaiting(() -> {
            Work accepted = fixture.stored(id);
            accepted.accept(firstAcceptedAt);
            fixture.workRepository.store(accepted);
        });

        service.accept(command(id.value()));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.events.published()).isEmpty();
        assertThat(fixture.stored(id).assignmentHistory().getLast().decidedAt()).contains(firstAcceptedAt);
    }

    @Test
    @DisplayName("기사 잠금 대기 한도를 넘기면 작업을 찾기 전에 잠시 후 다시 시도하라는 오류다")
    void reportsBusyTechnicianBeforeReadingWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(UNKNOWN_WORK_ID)), ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"ACCEPTED", "IN_PROGRESS", "COMPLETED"})
    @DisplayName("이미 수락해 그대로인 배정에 수락을 다시 보내면 아무것도 바꾸지 않고 발행하지 않은 채 성공한다")
    void treatsRepeatedAcceptanceAsSuccess(WorkStatus status) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(status);

        service.accept(command(id.value()));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.events.published()).isEmpty();
        Work stored = fixture.stored(id);
        assertThat(stored.status()).isEqualTo(status);
        assertThat(stored.assignmentHistory().getLast().decidedAt()).contains(ACCEPTED_AT);
    }

    @Test
    @DisplayName("수락한 뒤 일정만 바뀌어 수락을 이은 배정은 새 순번으로 수락을 보내도 중복 전송으로 성공하고, 이전 순번은 낡은 배정이다")
    void treatsAcceptanceOfKeptAcceptanceAsRepeated() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.reschedule(TEN.plusSeconds(3_600), TWO_HOURS, NOW, SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        service.accept(command(id.value(), 2));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.events.published()).isEmpty();
        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(id.value(), 1)), ScheduleErrorCode.ASSIGNMENT_CHANGED);
    }

    @Test
    @DisplayName("이미 거절한 작업은 기사가 볼 수 없어 수락하려 하면 찾을 수 없음이다")
    void hidesRejectedWorkFromAcceptance() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(id.value())), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("응답 시각이 배정 시각보다 앞서면(서버 간 시계 차이) 입력 오류다")
    void rejectsResponseBeforeAssignment() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = rescheduledAfterNow();

        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(id.value(), 2)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("응답할 수 없는 상태면 응답 시각이 배정 시각보다 앞서도 상태 오류다")
    void checksStateBeforeResponseTime() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(rescheduledAfterNow());
        work.cancel(NOW.plusSeconds(120), SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejectedWithoutSaving(
                () -> service.accept(command(id.value(), 2)), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    /** 수락대기 작업을 고정 시계({@link ScheduleServiceFixture#NOW})보다 늦은 시각에 같은 기사의 다른 시간으로 바꿔 둔다. 새 배정은 2번이다. */
    private WorkId rescheduledAfterNow() {
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.reschedule(TEN.plusSeconds(3_600), TWO_HOURS, NOW.plusSeconds(60), SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    private static AcceptWorkCommand command(long workId) {
        return command(workId, 1);
    }

    private static AcceptWorkCommand command(long workId, int number) {
        return new AcceptWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, number);
    }
}
