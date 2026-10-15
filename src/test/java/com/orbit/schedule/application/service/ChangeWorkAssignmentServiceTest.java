package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.ChangeWorkAssignmentCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo.ConflictingWork;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 배정 변경(배정·재배정·일정 변경 통합)")
class ChangeWorkAssignmentServiceTest {

    private static final Instant ELEVEN = TEN.plus(Duration.ofHours(1));

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final ChangeWorkAssignmentService service = new ChangeWorkAssignmentService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.technicianContracts,
            fixture.scheduleLock,
            fixture.clock);

    @Test
    @DisplayName("대기함 작업은 배정해 수락대기로 만들고, 기사를 잠근 뒤 기사 계약을 확인한다")
    void assignsBacklogWork() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result = service.change(command(id, TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.assignedBy()).isEqualTo(MANAGER_ID);
            assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        });
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.technicianContracts.checks()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 기사로 옮기면 재배정하고, 새 기사를 잠근 뒤 기사 계약을 확인한다")
    void reassignsToOtherTechnician() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.ACCEPTED);

        ScheduleChangeInfo result = service.change(command(id, OTHER_TECHNICIAN_ID.value(), ELEVEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(OTHER_TECHNICIAN_ID, ELEVEN, TWO_HOURS));
        assertThat(saved.assignmentHistory().getFirst().ending())
                .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.REASSIGNED));
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, OTHER_TECHNICIAN_ID, 0));
        assertThat(fixture.technicianContracts.checks()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 기사에서 시간만 옮기면 일정 변경이고, 현재 담당기사는 다시 확인하지 않는다")
    void reschedulesForSameTechnician() {
        fixture.givenManager();
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        ScheduleChangeInfo result = service.change(command(id, TECHNICIAN_ID.value(), ELEVEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        Work saved = fixture.singleSaved();
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, ELEVEN, TWO_HOURS));
        assertThat(saved.assignmentHistory().getFirst().ending())
                .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.RESCHEDULED));
        assertThat(fixture.technicianContracts.checks()).isZero();
    }

    @Test
    @DisplayName("같은 기사·같은 시간이면 바꿀 것이 없어 같은 일정 오류다")
    void rejectsUnchangedSchedule() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        fixture.assertRejected(
                () -> service.change(command(id, TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.SCHEDULE_UNCHANGED);
    }

    @Test
    @DisplayName("배정·재배정할 기사가 활성 기사 계약이 아니면 잠근 뒤 저장하지 않고 배정할 수 없는 기사 오류다")
    void rejectsInactiveTechnicianForAssignAndReassign() {
        fixture.givenManager();
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, OTHER_TECHNICIAN_ID);
        WorkId backlog = fixture.givenWork(WorkStatus.REGISTERED);
        WorkId assigned = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        for (WorkId id : new WorkId[] {backlog, assigned}) {
            assertThatThrownBy(() -> service.change(command(id, OTHER_TECHNICIAN_ID.value(), ELEVEN, TWO_HOURS, true)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.TECHNICIAN_NOT_ASSIGNABLE));
        }
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isZero();
    }

    @Test
    @DisplayName("같은 기사의 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고 겹친 작업을 돌려준다")
    void withholdsUnconfirmedConflict() {
        fixture.givenManager();
        WorkId existing =
                fixture.givenWork(ORGANIZATION_ID, "기존 작업", WorkStatus.PENDING_ACCEPTANCE, OTHER_TECHNICIAN_ID, ELEVEN);
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result =
                service.change(command(target, OTHER_TECHNICIAN_ID.value(), ELEVEN, TWO_HOURS, false));

        assertThat(result.applied()).isFalse();
        assertThat(result.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    @DisplayName("작업중·완료·취소된 작업은 기사·시간과 관계없이 상태 오류다")
    void rejectsUnchangeableStatus(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        for (long technicianId : new long[] {TECHNICIAN_ID.value(), OTHER_TECHNICIAN_ID.value()}) {
            fixture.assertRejected(
                    () -> service.change(command(id, technicianId, ELEVEN, TWO_HOURS, false)),
                    ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    @Test
    @DisplayName("기사·시작시각·소요시간이 잘못되면 상태보다 먼저 입력 오류다")
    void checksInputBeforeState() {
        fixture.givenManager();
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.assertRejected(
                () -> service.change(command(completed, 0L, TEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.change(command(completed, TECHNICIAN_ID.value(), null, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.change(command(completed, TECHNICIAN_ID.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static ChangeWorkAssignmentCommand command(
            WorkId workId, Long technicianId, Instant start, Duration duration, boolean confirmed) {
        return new ChangeWorkAssignmentCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId.value(), technicianId, start, duration, confirmed);
    }
}
