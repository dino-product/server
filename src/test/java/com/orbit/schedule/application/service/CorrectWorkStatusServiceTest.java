package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CorrectWorkStatusCommand;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.StatusCorrection;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("관리자 강제 상태 변경")
class CorrectWorkStatusServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final CorrectWorkStatusService service = new CorrectWorkStatusService(
            fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.clock);

    @Test
    @DisplayName("총관리자가 완료를 작업중으로 되돌리면 지금 시각·처리자·사유를 정정 기록에 남기고, 기사를 잠가 다른 작업중 작업이 없는지 확인한다")
    void correctsCompletedToInProgress() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.COMPLETED);

        service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "잘못 완료 처리"));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(saved.completionReport()).isEmpty();
        assertThat(saved.statusCorrections()).singleElement().satisfies(correction -> {
            assertThat(correction.from()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(correction.correctedAt()).isEqualTo(NOW);
            assertThat(correction.correctedBy()).isEqualTo(MANAGER_ID);
            assertThat(correction.reason()).isEqualTo("잘못 완료 처리");
        });
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("작업중 → 수락됨, 취소 → 대기함은 기사의 작업중 작업을 늘리지 않으므로 잠그지 않는다")
    void correctsWithoutLockWhenNotReturningToInProgress() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId inProgress = fixture.givenWork(WorkStatus.IN_PROGRESS);
        WorkId cancelled = fixture.givenWork(WorkStatus.CANCELLED);

        service.correct(command(inProgress.value(), WorkStatus.ACCEPTED, "잘못 시작"));
        service.correct(command(cancelled.value(), WorkStatus.REGISTERED, "잘못 취소"));

        assertThat(fixture.stored(inProgress).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.stored(cancelled).status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(fixture.stored(cancelled).schedule()).isEmpty();
        assertThat(fixture.stored(cancelled).statusCorrections())
                .extracting(StatusCorrection::from)
                .containsExactly(WorkStatus.CANCELLED);
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @Test
    @DisplayName("작업중으로 되돌릴 때 같은 기사에게 작업중인 다른 작업이 있으면 되돌리지 않는다")
    void rejectsWhenTechnicianAlreadyWorking() {
        fixture.givenManager(ActorRole.OWNER);
        fixture.givenWork(
                ORGANIZATION_ID, "작업중인 작업", WorkStatus.IN_PROGRESS, TECHNICIAN_ID, TEN.plus(Duration.ofHours(5)));
        WorkId id = fixture.givenWork(WorkStatus.COMPLETED);

        assertThatThrownBy(() -> service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "잘못 완료")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_ALREADY_WORKING));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.COMPLETED);
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경을 기다리다 대기 한도를 넘기면 잠시 후 다시 시도하라는 오류다")
    void reportsBusyTechnician() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.COMPLETED);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(
                () -> service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "잘못 완료")),
                ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
    }

    @Test
    @DisplayName("직원과 기사는 작업·입력과 관계없이 권한 오류다")
    void allowsOnlyOwner() {
        WorkId id = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.givenManager(ActorRole.STAFF);
        fixture.assertRejected(
                () -> service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "정정")),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.assertRejected(() -> service.correct(command(0L, null, " ")), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.assertRejected(
                () -> service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "정정")),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("도착 상태·사유가 잘못되면 되돌릴 수 없는 상태라도 상태보다 먼저 입력 오류다")
    void checksInputBeforeState() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);

        fixture.assertRejected(
                () -> service.correct(command(accepted.value(), null, "정정")), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.correct(command(accepted.value(), WorkStatus.REGISTERED, " ")),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.correct(command(accepted.value(), WorkStatus.REGISTERED, "가".repeat(256))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("한 단계 되돌리기가 아닌 정정은 상태 오류다")
    void rejectsDisallowedCorrection() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.assertRejected(
                () -> service.correct(command(accepted.value(), WorkStatus.REGISTERED, "정정")),
                ScheduleErrorCode.INVALID_WORK_STATE);
        fixture.assertRejected(
                () -> service.correct(command(completed.value(), WorkStatus.ACCEPTED, "정정")),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("정정 시각이 작업의 가장 늦은 기록보다 앞서면(서버 간 시계 차이) 잠그기 전에 입력 오류다")
    void rejectsCorrectionBeforeLatestRecord() {
        fixture.givenManager(ActorRole.OWNER);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.start(ACCEPTED_AT.plusSeconds(60));
        work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), NOW.plusSeconds(60));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(
                () -> service.correct(command(id.value(), WorkStatus.IN_PROGRESS, "정정")),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @Test
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈추고, 없는 작업은 찾을 수 없음이다")
    void checksAccountAndWork() {
        assertThatThrownBy(() -> service.correct(
                        new CorrectWorkStatusCommand(null, ORGANIZATION_ID.value(), 1L, WorkStatus.IN_PROGRESS, "정정")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        fixture.givenManager(ActorRole.OWNER);
        fixture.assertRejected(
                () -> service.correct(command(UNKNOWN_WORK_ID, WorkStatus.IN_PROGRESS, "정정")),
                ScheduleErrorCode.WORK_NOT_FOUND);
    }

    private static CorrectWorkStatusCommand command(long workId, WorkStatus target, String reason) {
        return new CorrectWorkStatusCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, target, reason);
    }
}
