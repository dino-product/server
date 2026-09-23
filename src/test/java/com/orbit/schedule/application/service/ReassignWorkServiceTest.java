package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult.ConflictingWork;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("담당기사 재배정")
class ReassignWorkServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final MembershipId CURRENT_TECHNICIAN = new MembershipId(3L);
    private static final MembershipId NEW_TECHNICIAN = new MembershipId(4L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant ASSIGNED_AT = NOW.minus(Duration.ofHours(2));
    private static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    private static final long UNKNOWN_WORK_ID = 999L;

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final FakeLockTechnicianSchedulePort scheduleLock = new FakeLockTechnicianSchedulePort(workRepository);
    private final ReassignWorkService service =
            new ReassignWorkService(actorPort, workRepository, scheduleLock, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("수락대기 작업을 새 기사에게 넘기면 대기 중이던 배정을 마감하고 새 기사의 수락을 기다린다")
    void reassignsPendingWork() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", CURRENT_TECHNICIAN, TEN, false);

        ScheduleChangeResult result = service.reassign(command(id.value(), NEW_TECHNICIAN.value(), TEN, false));

        assertThat(result.applied()).isTrue();
        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(NEW_TECHNICIAN, TEN, Duration.ofHours(2)));
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.REASSIGNED, AssignmentResult.PENDING);
        assertThat(saved.assignmentHistory().getLast().assignedAt()).isEqualTo(NOW);
        assertThat(scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, NEW_TECHNICIAN, 0));
    }

    @Test
    @DisplayName("수락된 작업을 새 기사에게 넘기면 수락 이력은 두고 새 기사에게 다시 수락받는다")
    void reassignsAcceptedWork() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", CURRENT_TECHNICIAN, TEN, true);

        service.reassign(command(id.value(), NEW_TECHNICIAN.value(), TEN, false));

        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.ACCEPTED, AssignmentResult.PENDING);
    }

    @Test
    @DisplayName("현재 기사가 아닌 새 기사의 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고, 확인했으면 반영한다")
    void checksConflictsOfNewTechnician() {
        givenManager();
        WorkId existing = givenAssignedWork(ORGANIZATION_ID, "새 기사 작업", NEW_TECHNICIAN, TEN, false);
        givenAssignedWork(ORGANIZATION_ID, "현재 기사의 다른 작업", CURRENT_TECHNICIAN, TEN, false);
        WorkId target = givenAssignedWork(ORGANIZATION_ID, "대상 작업", CURRENT_TECHNICIAN, TEN, false);

        ScheduleChangeResult withheld = service.reassign(command(target.value(), NEW_TECHNICIAN.value(), TEN, false));

        assertThat(withheld.applied()).isFalse();
        assertThat(withheld.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(workRepository.saved()).isEmpty();
        assertThat(workRepository
                        .findById(target)
                        .orElseThrow()
                        .schedule()
                        .orElseThrow()
                        .technicianId())
                .isEqualTo(CURRENT_TECHNICIAN);

        ScheduleChangeResult applied = service.reassign(command(target.value(), NEW_TECHNICIAN.value(), TEN, true));

        assertThat(applied.applied()).isTrue();
        assertThat(applied.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(singleSaved().schedule().orElseThrow().technicianId()).isEqualTo(NEW_TECHNICIAN);
    }

    @Test
    @DisplayName("현재 담당기사에게 다시 넘기면 입력 오류로 거부한다")
    void rejectsSameTechnician() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", CURRENT_TECHNICIAN, TEN, false);

        assertErrorWithoutSave(
                () -> service.reassign(command(id.value(), CURRENT_TECHNICIAN.value(), TEN, true)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("배정되지 않은 작업은 겹침 경고보다 먼저 상태 오류로 거부한다")
    void rejectsUnassignedWorkBeforeConflictWarning() {
        givenManager();
        givenAssignedWork(ORGANIZATION_ID, "새 기사 작업", NEW_TECHNICIAN, TEN, false);
        WorkId registered = workRepository
                .save(Work.register(ORGANIZATION_ID, "대기함 작업", new MembershipId(1L), null, null, null))
                .id()
                .orElseThrow();
        workRepository.clearSaveHistory();

        assertErrorWithoutSave(
                () -> service.reassign(command(registered.value(), NEW_TECHNICIAN.value(), TEN, false)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("기사는 작업 존재나 입력 오류보다 먼저 권한 오류를 받는다")
    void checksPermissionFirst() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);

        assertErrorWithoutSave(
                () -> service.reassign(command(UNKNOWN_WORK_ID, 0L, null, false)),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("다른 조직·없는 작업은 일정 입력이 잘못돼도 찾을 수 없음으로 처리한다")
    void hidesWorkOfOtherOrganization() {
        givenManager();
        WorkId otherOrganizationWork =
                givenAssignedWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", CURRENT_TECHNICIAN, TEN, false);

        assertErrorWithoutSave(
                () -> service.reassign(command(otherOrganizationWork.value(), NEW_TECHNICIAN.value(), TEN, true)),
                ScheduleErrorCode.WORK_NOT_FOUND);
        assertErrorWithoutSave(
                () -> service.reassign(command(UNKNOWN_WORK_ID, 0L, null, true)), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("작업 식별자·새 기사 식별자·일정이 잘못되면 입력 오류로 거부한다")
    void rejectsInvalidInput() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", CURRENT_TECHNICIAN, TEN, false);

        assertErrorWithoutSave(
                () -> service.reassign(command(0L, NEW_TECHNICIAN.value(), TEN, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);

        assertErrorWithoutSave(
                () -> service.reassign(command(id.value(), 0L, TEN, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.reassign(command(id.value(), NEW_TECHNICIAN.value(), null, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private void givenManager() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
    }

    private WorkId givenAssignedWork(
            OrganizationId organizationId,
            String name,
            MembershipId technicianId,
            Instant startTime,
            boolean accepted) {
        Work work = Work.register(organizationId, name, new MembershipId(1L), null, null, null);
        work.assign(new WorkSchedule(technicianId, startTime, Duration.ofHours(2)), ASSIGNED_AT);
        if (accepted) {
            work.accept(ASSIGNED_AT.plus(Duration.ofMinutes(30)));
        }
        WorkId id = workRepository.save(work).id().orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private static ReassignWorkCommand command(long workId, Long technicianId, Instant startTime, boolean confirmed) {
        return new ReassignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, technicianId, startTime, Duration.ofHours(2), confirmed);
    }

    private Work singleSaved() {
        assertThat(workRepository.saved()).hasSize(1);
        return workRepository.saved().getFirst();
    }

    private void assertErrorWithoutSave(Runnable call, ScheduleErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
        assertThat(workRepository.saved()).isEmpty();
    }
}
