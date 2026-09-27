package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 시작")
class StartWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final StartWorkService service =
            new StartWorkService(fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.clock);

    @Test
    @DisplayName("담당 기사가 수락한 최신 배정의 작업을 시작하면 지금 시각을 시작 시각으로 남기고, 작업중 판정 전에 기사를 잠근다")
    void startsAcceptedWork() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        service.start(command(id.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(saved.startedAt()).contains(NOW);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 기사나 다른 조직의 작업중인 작업은 시작을 막지 않는다")
    void ignoresInProgressWorkOfOthers() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.IN_PROGRESS, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        service.start(command(id.value()));

        assertThat(fixture.singleSaved().status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("같은 기사에게 이미 작업중인 다른 작업이 있으면 시작하지 않는다")
    void rejectsWhenAnotherWorkIsInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(
                ORGANIZATION_ID, "작업중인 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN.plus(Duration.ofHours(5)));
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        assertThatThrownBy(() -> service.start(command(id.value())))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_ALREADY_WORKING));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 같은 작업의 시작이 먼저 저장됐어도 자신을 다른 작업중 작업으로 세지 않는다")
    void doesNotCountItselfAsAnotherWorkInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);
        fixture.scheduleLock.whileWaiting(() -> {
            Work startedFirst = fixture.stored(id);
            startedFirst.start(ACCEPTED_AT.plusSeconds(1));
            fixture.workRepository.store(startedFirst);
        });

        service.start(command(id.value()));

        assertThat(fixture.singleSaved().status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"IN_PROGRESS", "COMPLETED"})
    @DisplayName("배정이 그대로인 채 이미 시작한 작업(완료 포함)에 시작을 다시 보내면 잠그지도 바꾸지도 않고 성공한다")
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
        work.cancel(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("수락 전이면 같은 기사의 작업중 작업이 있어도 잠그기 전에 상태 오류다")
    void checksStateBeforeLockAndInProgress() {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.givenWork(
                ORGANIZATION_ID, "작업중인 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN.plus(Duration.ofHours(5)));
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("시작 시각이 수락 시각보다 앞서면(서버 간 시계 차이) 잠그기 전에 입력 오류다")
    void rejectsStartBeforeAcceptance() {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE));
        work.accept(NOW.plusSeconds(60));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경을 기다리다 대기 한도를 넘기면 작업중 여부를 읽지 않고 잠시 후 다시 시도하라는 오류다")
    void reportsBusyTechnicianWithoutReading() {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(() -> service.start(command(id.value())), ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isZero();
    }

    private static StartWorkCommand command(long workId) {
        return new StartWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1);
    }
}
