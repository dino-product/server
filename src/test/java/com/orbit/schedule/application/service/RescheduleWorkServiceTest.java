package com.orbit.schedule.application.service;

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
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.WorkRescheduled;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo.ConflictingWork;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("일정 변경")
class RescheduleWorkServiceTest {

    private static final Instant ELEVEN = TEN.plus(Duration.ofHours(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final RescheduleWorkService service = new RescheduleWorkService(
            fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.events, fixture.clock);

    @Test
    @DisplayName("수락된 작업의 시간을 바꾸면 같은 기사를 잠그고 판정해, 다시 수락받지 않고 작업전을 유지한 채 수락 뒤 일정 변경을 발행한다")
    void reschedulesAcceptedWorkKeepingAcceptance() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        ScheduleChangeInfo result = service.reschedule(command(id.value(), ELEVEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, ELEVEN, TWO_HOURS));
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.ACCEPTED, AssignmentResult.ACCEPTED);
        assertThat(saved.assignmentHistory().getFirst().ending())
                .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.RESCHEDULED));
        assertThat(saved.assignmentHistory().getLast().assignedBy()).isEqualTo(MANAGER_ID);
        assertThat(saved.assignmentHistory().getLast().decidedAt()).contains(NOW);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkRescheduled(
                                id.value(), ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), true, "대상 작업", ELEVEN, NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(id.value()), NOW));
    }

    @Test
    @DisplayName("작업 자신의 기존 일정과 겹치는 것은 겹침으로 보지 않고, 대기 중이던 배정은 지금 시각으로 회수해 바뀐 시간으로 수락을 기다리며 수락 전 일정 변경을 발행한다")
    void ignoresOwnPreviousSchedule() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        ScheduleChangeInfo result = service.reschedule(command(id.value(), ELEVEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        assertThat(saved.assignmentHistory().getFirst()).satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
            assertThat(history.decidedAt()).contains(NOW);
        });
        assertThat(saved.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkRescheduled(
                                id.value(),
                                ORGANIZATION_ID.value(),
                                TECHNICIAN_ID.value(),
                                false,
                                "대상 작업",
                                ELEVEN,
                                NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(id.value()), NOW));
    }

    @Test
    @DisplayName("수락된 작업의 시간 변경이 겹침 미확인으로 보류되면 발행하지 않고, 확인해 반영하면 그때 발행한다")
    void publishesRescheduleOnlyWhenApplied() {
        fixture.givenManager(ActorRole.OWNER);
        fixture.givenWork(
                ORGANIZATION_ID, "기존 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, ELEVEN.plus(Duration.ofHours(1)));
        WorkId target = fixture.givenWork(WorkStatus.ACCEPTED);

        ScheduleChangeInfo withheld = service.reschedule(command(target.value(), ELEVEN, TWO_HOURS, false));

        assertThat(withheld.applied()).isFalse();
        assertThat(fixture.events.published()).isEmpty();

        service.reschedule(command(target.value(), ELEVEN, TWO_HOURS, true));

        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkRescheduled(
                                target.value(),
                                ORGANIZATION_ID.value(),
                                TECHNICIAN_ID.value(),
                                true,
                                "대상 작업",
                                ELEVEN,
                                NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(target.value()), NOW));
    }

    @Test
    @DisplayName("같은 기사의 다른 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고, 확인했으면 반영한다")
    void checksConflictsOfSameTechnician() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId existing = fixture.givenWork(
                ORGANIZATION_ID, "기존 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, ELEVEN.plus(Duration.ofHours(1)));
        Instant yesterday = TEN.minus(Duration.ofDays(1));
        WorkId target =
                fixture.givenWork(ORGANIZATION_ID, "대상 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, yesterday);

        ScheduleChangeInfo withheld = service.reschedule(command(target.value(), ELEVEN, TWO_HOURS, false));

        assertThat(withheld.applied()).isFalse();
        assertThat(withheld.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(target).schedule().orElseThrow().startTime()).isEqualTo(yesterday);

        ScheduleChangeInfo applied = service.reschedule(command(target.value(), ELEVEN, TWO_HOURS, true));

        assertThat(applied.applied()).isTrue();
        assertThat(applied.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(fixture.singleSaved().schedule()).contains(new WorkSchedule(TECHNICIAN_ID, ELEVEN, TWO_HOURS));
    }

    @Test
    @DisplayName("같은 시간으로 바꾸면 입력 오류와 구분되는 일정 그대로 오류다")
    void rejectsUnchangedSchedule() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.SCHEDULE_UNCHANGED);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "IN_PROGRESS", "COMPLETED", "CANCELED"})
    @DisplayName("수락대기·수락됨이 아니면 담당기사·일정이 남아 있든 없든 상태 오류다")
    void rejectsUnchangeableStatus(WorkStatus status) {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), ELEVEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "CANCELED"})
    @DisplayName("시간 입력이 잘못되면 담당기사가 없는 작업이라도 상태 오류보다 먼저 입력 오류다")
    void checksInputBeforeState(WorkStatus status) {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), null, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("시작시각이 없거나 소요시간이 0 이하이거나 24시간을 넘으면 입력 오류다")
    void rejectsInvalidTime() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), null, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofHours(25)}) {
            fixture.assertRejected(
                    () -> service.reschedule(command(id.value(), ELEVEN, duration, false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @Test
    @DisplayName("변경 시각이 직전 이력의 응답 시각보다 앞서면 입력 오류다")
    void rejectsRescheduleBeforeLatestHistory() {
        fixture.givenManager(ActorRole.OWNER);
        Work work = Work.register(ORGANIZATION_ID, "나중에 수락된 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, MANAGER_ID);
        work.accept(NOW.plus(Duration.ofHours(1)));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), ELEVEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경을 기다리다 잠금에 실패하면 변경 중 오류다")
    void reportsBusyTechnician() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(
                () -> service.reschedule(command(id.value(), ELEVEN, TWO_HOURS, false)),
                ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
    }

    private static RescheduleWorkCommand command(long workId, Instant start, Duration duration, boolean confirmed) {
        return new RescheduleWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, start, duration, confirmed);
    }
}
