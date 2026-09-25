package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo.ConflictingWork;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("담당기사 재배정")
class ReassignWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final ReassignWorkService service =
            new ReassignWorkService(fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.clock);

    @Test
    @DisplayName("수락대기 작업을 새 기사에게 넘기면 대기 중이던 배정을 지금 시각으로 회수하고 새 기사의 수락을 기다린다")
    void reassignsPendingWork() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        ScheduleChangeInfo result =
                service.reassign(command(id.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result, history -> history.decidedAt()
                        .orElse(null))
                .containsExactly(
                        Tuple.tuple(AssignmentResult.WITHDRAWN, NOW), Tuple.tuple(AssignmentResult.PENDING, null));
        assertThat(saved.assignmentHistory().getFirst().ending())
                .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.REASSIGNED));
        assertThat(saved.assignmentHistory().getLast().assignedAt()).isEqualTo(NOW);
        assertThat(saved.assignmentHistory().getLast().assignedBy()).isEqualTo(MANAGER_ID);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, OTHER_TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("수락된 작업을 새 기사에게 넘기면 수락 이력은 두고 새 기사에게 다시 수락받는다")
    void reassignsAcceptedWork() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        service.reassign(command(id.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.ACCEPTED, AssignmentResult.PENDING);
        assertThat(saved.assignmentHistory().getFirst().decidedAt()).contains(ACCEPTED_AT);
    }

    @Test
    @DisplayName("현재 기사가 아닌 새 기사의 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고, 확인했으면 반영한다")
    void checksConflictsOfNewTechnician() {
        fixture.givenManager();
        WorkId newTechnicianWork =
                fixture.givenWork(ORGANIZATION_ID, "새 기사 작업", WorkStatus.PENDING_ACCEPTANCE, OTHER_TECHNICIAN_ID, TEN);
        fixture.givenWork(ORGANIZATION_ID, "현재 기사의 다른 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);
        WorkId target = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        ScheduleChangeInfo withheld =
                service.reassign(command(target.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(withheld.applied()).isFalse();
        assertThat(withheld.conflicts()).extracting(ConflictingWork::workId).containsExactly(newTechnicianWork.value());
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(target).schedule().orElseThrow().technicianId())
                .isEqualTo(TECHNICIAN_ID);

        ScheduleChangeInfo applied =
                service.reassign(command(target.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, true));

        assertThat(applied.applied()).isTrue();
        assertThat(applied.conflicts()).extracting(ConflictingWork::workId).containsExactly(newTechnicianWork.value());
        assertThat(fixture.singleSaved().schedule().orElseThrow().technicianId())
                .isEqualTo(OTHER_TECHNICIAN_ID);
    }

    @Test
    @DisplayName("현재 담당기사에게 다시 넘기면 입력 오류와 구분되는 같은 기사 오류다")
    void rejectsSameTechnician() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        fixture.assertRejected(
                () -> service.reassign(command(id.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.SAME_TECHNICIAN);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    @DisplayName("수락대기·수락됨이 아니면 현재 기사로 넘기더라도 같은 기사 오류보다 먼저 상태 오류다")
    void rejectsUnchangeableStatus(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        for (long technicianId : new long[] {OTHER_TECHNICIAN_ID.value(), TECHNICIAN_ID.value()}) {
            fixture.assertRejected(
                    () -> service.reassign(command(id.value(), technicianId, TEN, TWO_HOURS, false)),
                    ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    @Test
    @DisplayName("일정 입력이 잘못되면 상태 오류보다 먼저 입력 오류다")
    void checksInputBeforeState() {
        fixture.givenManager();
        WorkId registered = fixture.givenWork(WorkStatus.REGISTERED);

        fixture.assertRejected(
                () -> service.reassign(
                        command(registered.value(), OTHER_TECHNICIAN_ID.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("새 기사 식별자·시작시각·소요시간이 잘못되면 입력 오류다")
    void rejectsInvalidScheduleInput() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        fixture.assertRejected(
                () -> service.reassign(command(id.value(), 0L, TEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reassign(command(id.value(), OTHER_TECHNICIAN_ID.value(), null, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.reassign(
                        command(id.value(), OTHER_TECHNICIAN_ID.value(), TEN, Duration.ofHours(25), false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("재배정 시각이 직전 이력의 응답 시각보다 앞서면 입력 오류다")
    void rejectsReassignmentBeforeLatestHistory() {
        fixture.givenManager();
        Work work = Work.register(ORGANIZATION_ID, "나중에 수락된 작업", REGISTRAR_ID, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), NOW, MANAGER_ID);
        work.accept(NOW.plus(Duration.ofHours(1)));
        WorkId id = fixture.workRepository.store(work);

        fixture.assertRejected(
                () -> service.reassign(command(id.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("새 기사의 앞선 변경을 기다리다 잠금에 실패하면 변경 중 오류다")
    void reportsBusyTechnician() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(
                () -> service.reassign(command(id.value(), OTHER_TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
    }

    private static ReassignWorkCommand command(
            long workId, Long technicianId, Instant start, Duration duration, boolean confirmed) {
        return new ReassignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, technicianId, start, duration, confirmed);
    }
}
