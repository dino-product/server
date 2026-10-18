package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 시작")
class StartWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final StartWorkService service =
            new StartWorkService(fixture.actorPort, fixture.workRepository, fixture.clock);

    @Test
    @DisplayName("담당 기사가 수락한 최신 배정의 작업을 시작하면 지금 시각을 시작 시각으로 남기고, 기사의 다른 작업은 읽지도 잠그지도 않는다")
    void startsAcceptedWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = acceptedWorkStartingAt(NOW);

        service.start(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(saved.startedAt()).contains(NOW);
        assertThat(fixture.scheduleLock.locks()).isEmpty();
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isZero();
    }

    @Test
    @DisplayName("같은 기사에게 이미 작업중인 다른 작업이 있어도 시작해 함께 작업중이 된다")
    void startsWhileAnotherWorkIsInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId inProgress = fixture.givenWork(
                ORGANIZATION_ID, "작업중인 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN.plus(Duration.ofHours(5)));
        WorkId id = acceptedWorkStartingAt(NOW);

        service.start(command(id.value()));

        assertThat(fixture.singleSaved().status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(fixture.stored(inProgress).status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("예정 시작 10분 전 정각과 예정 종료 정각에도 시작할 수 있다")
    void startsAtEdgesOfStartWindow() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId earliest = acceptedWorkStartingAt(NOW.plus(Duration.ofMinutes(10)));
        WorkId latest = acceptedWorkStartingAt(NOW.minus(TWO_HOURS));

        service.start(command(earliest.value()));
        service.start(command(latest.value()));

        assertThat(fixture.workRepository.saved())
                .extracting(Work::status)
                .containsExactly(WorkStatus.IN_PROGRESS, WorkStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("예정 시작 10분 전보다 이르면 시작하지 않고 아직 시작할 수 없다는 오류다")
    void rejectsStartBeforeStartWindow() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = acceptedWorkStartingAt(NOW.plus(Duration.ofMinutes(10)).plusNanos(1_000));

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.TOO_EARLY_TO_START);
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.stored(id).startedAt()).isEmpty();
    }

    @Test
    @DisplayName("예정 종료 시각이 지났으면 시작하지 않고 예정 시간이 지났다는 오류다")
    void rejectsStartAfterScheduledEnd() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = acceptedWorkStartingAt(NOW.minus(TWO_HOURS).minusNanos(1_000));

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.START_TIME_PASSED);
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.stored(id).startedAt()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"IN_PROGRESS", "COMPLETED"})
    @DisplayName("배정이 그대로인 채 이미 시작한 작업(완료 포함)에 시작을 다시 보내면 시작 가능 시간 밖이어도 바꾸지 않고 성공한다")
    void treatsRepeatedStartAsSuccess(WorkStatus status) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(status);

        service.start(command(id.value()));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).isEmpty();
        assertThat(fixture.stored(id).startedAt()).contains(ACCEPTED_AT);
    }

    @Test
    @DisplayName("작업중에 취소된 작업은 이미 시작했어도 다시 보낸 시작을 성공으로 보지 않고 상태 오류다")
    void rejectsStartAfterCancellationInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.IN_PROGRESS));
        work.cancel(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("수락 전이면 시작 가능 시간 밖이어도 상태 오류다")
    void checksStateBeforeStartWindow() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("시작 시각이 수락 시각보다 앞서면(서버 간 시계 차이) 시작 가능 시간 밖이어도 입력 오류다")
    void rejectsStartBeforeAcceptance() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPT));
        work.accept(NOW.plusSeconds(60));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}가 수락한, 주어진 시각에 시작하는 2시간 일정의 작업. */
    private WorkId acceptedWorkStartingAt(Instant start) {
        return fixture.givenWork(ORGANIZATION_ID, "대상 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, start);
    }

    private static StartWorkCommand command(long workId) {
        return new StartWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1);
    }
}
