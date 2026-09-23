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
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
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

@DisplayName("일정 변경")
class RescheduleWorkServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant ASSIGNED_AT = NOW.minus(Duration.ofHours(2));
    private static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    private static final Instant ELEVEN = TEN.plus(Duration.ofHours(1));
    private static final long UNKNOWN_WORK_ID = 999L;

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final FakeLockTechnicianSchedulePort scheduleLock = new FakeLockTechnicianSchedulePort(workRepository);
    private final RescheduleWorkService service =
            new RescheduleWorkService(actorPort, workRepository, scheduleLock, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("수락된 작업의 시간을 바꾸면 같은 기사에게 다시 수락받는다")
    void reschedulesAcceptedWork() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", TEN, true);

        ScheduleChangeResult result = service.reschedule(command(id.value(), ELEVEN, false));

        assertThat(result.applied()).isTrue();
        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, ELEVEN, Duration.ofHours(2)));
        assertThat(saved.assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.ACCEPTED, AssignmentResult.PENDING);
        assertThat(scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("작업 자신의 기존 일정과 겹치는 것은 겹침으로 보지 않는다")
    void ignoresOwnPreviousSchedule() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", TEN, false);

        ScheduleChangeResult result = service.reschedule(command(id.value(), ELEVEN, false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
        assertThat(singleSaved().assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.REASSIGNED, AssignmentResult.PENDING);
    }

    @Test
    @DisplayName("같은 기사의 다른 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고, 확인했으면 반영한다")
    void checksConflictsOfSameTechnician() {
        givenManager();
        WorkId existing = givenAssignedWork(ORGANIZATION_ID, "기존 작업", ELEVEN.plus(Duration.ofHours(1)), false);
        WorkId target = givenAssignedWork(ORGANIZATION_ID, "대상 작업", TEN.minus(Duration.ofDays(1)), false);

        ScheduleChangeResult withheld = service.reschedule(command(target.value(), ELEVEN, false));

        assertThat(withheld.applied()).isFalse();
        assertThat(withheld.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(workRepository.saved()).isEmpty();
        assertThat(workRepository
                        .findById(target)
                        .orElseThrow()
                        .schedule()
                        .orElseThrow()
                        .startTime())
                .isEqualTo(TEN.minus(Duration.ofDays(1)));

        ScheduleChangeResult applied = service.reschedule(command(target.value(), ELEVEN, true));

        assertThat(applied.applied()).isTrue();
        assertThat(applied.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        assertThat(singleSaved().schedule()).contains(new WorkSchedule(TECHNICIAN_ID, ELEVEN, Duration.ofHours(2)));
    }

    @Test
    @DisplayName("같은 일정으로 바꾸면 입력 오류로 거부한다")
    void rejectsSameSchedule() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", TEN, false);

        assertErrorWithoutSave(
                () -> service.reschedule(command(id.value(), TEN, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("대기함·작업중·취소된 작업은 일정 입력이 잘못돼도 상태 오류로 거부한다")
    void rejectsUnchangeableStatusBeforeInput() {
        givenManager();
        Work registered = Work.register(ORGANIZATION_ID, "대기함 작업", new MembershipId(1L), null, null, null);
        Work inProgress = assignedWork("작업중 작업");
        inProgress.accept(ASSIGNED_AT.plus(Duration.ofMinutes(30)));
        inProgress.start();
        Work cancelled = assignedWork("취소된 작업");
        cancelled.cancel(ASSIGNED_AT.plus(Duration.ofMinutes(30)));

        for (Work work : new Work[] {registered, inProgress, cancelled}) {
            WorkId id = workRepository.save(work).id().orElseThrow();
            workRepository.clearSaveHistory();
            assertErrorWithoutSave(
                    () -> service.reschedule(command(id.value(), null, false)), ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    @Test
    @DisplayName("작업 식별자나 일정 입력이 잘못되면 입력 오류로 거부한다")
    void rejectsInvalidInput() {
        givenManager();
        WorkId id = givenAssignedWork(ORGANIZATION_ID, "대상 작업", TEN, false);

        assertErrorWithoutSave(
                () -> service.reschedule(command(0L, ELEVEN, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.reschedule(command(id.value(), null, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.reschedule(new RescheduleWorkCommand(
                        ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), ELEVEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("기사는 권한 오류를, 다른 조직·없는 작업은 찾을 수 없음을 받는다")
    void checksPermissionThenOrganization() {
        WorkId otherOrganizationWork = givenAssignedWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", TEN, false);
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);
        assertErrorWithoutSave(
                () -> service.reschedule(command(UNKNOWN_WORK_ID, null, false)), ScheduleErrorCode.ACTION_NOT_ALLOWED);

        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.OWNER);
        assertErrorWithoutSave(
                () -> service.reschedule(command(otherOrganizationWork.value(), ELEVEN, true)),
                ScheduleErrorCode.WORK_NOT_FOUND);
        assertErrorWithoutSave(
                () -> service.reschedule(command(UNKNOWN_WORK_ID, null, true)), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    private void givenManager() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
    }

    private static Work assignedWork(String name) {
        Work work = Work.register(ORGANIZATION_ID, name, new MembershipId(1L), null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)), ASSIGNED_AT);
        return work;
    }

    private WorkId givenAssignedWork(OrganizationId organizationId, String name, Instant startTime, boolean accepted) {
        Work work = Work.register(organizationId, name, new MembershipId(1L), null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, startTime, Duration.ofHours(2)), ASSIGNED_AT);
        if (accepted) {
            work.accept(ASSIGNED_AT.plus(Duration.ofMinutes(30)));
        }
        WorkId id = workRepository.save(work).id().orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private static RescheduleWorkCommand command(long workId, Instant startTime, boolean confirmed) {
        return new RescheduleWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, startTime, Duration.ofHours(2), confirmed);
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
