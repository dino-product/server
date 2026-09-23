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
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("배정 해제")
class UnassignWorkServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant ASSIGNED_AT = NOW.minus(Duration.ofHours(2));
    private static final long UNKNOWN_WORK_ID = 999L;

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final UnassignWorkService service =
            new UnassignWorkService(actorPort, workRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("수락대기 작업을 해제하면 대기 중이던 배정을 마감하고 대기함으로 돌린다")
    void unassignsPendingWork() {
        givenManager();
        WorkId id = givenWork(ORGANIZATION_ID, WorkStatus.PENDING_ACCEPTANCE);

        service.unassign(command(id.value()));

        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.REASSIGNED);
            assertThat(history.decidedAt()).contains(NOW);
        });
    }

    @Test
    @DisplayName("수락된 작업을 해제하면 수락 이력은 그대로 두고 대기함으로 돌린다")
    void unassignsAcceptedWork() {
        givenManager();
        WorkId id = givenWork(ORGANIZATION_ID, WorkStatus.ACCEPTED);

        service.unassign(command(id.value()));

        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(ASSIGNED_AT.plus(Duration.ofMinutes(30)));
        });
    }

    @Test
    @DisplayName("대기함·작업중 작업은 해제할 수 없다")
    void rejectsUnassignableStatus() {
        givenManager();
        WorkId registered = givenWork(ORGANIZATION_ID, WorkStatus.REGISTERED);
        WorkId inProgress = givenWork(ORGANIZATION_ID, WorkStatus.IN_PROGRESS);

        assertErrorWithoutSave(
                () -> service.unassign(command(registered.value())), ScheduleErrorCode.INVALID_WORK_STATE);
        assertErrorWithoutSave(
                () -> service.unassign(command(inProgress.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("기사는 권한 오류를, 다른 조직·없는 작업은 찾을 수 없음을, 틀린 식별자는 입력 오류를 받는다")
    void checksPermissionThenLookup() {
        WorkId otherOrganizationWork = givenWork(OTHER_ORGANIZATION_ID, WorkStatus.PENDING_ACCEPTANCE);
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);
        assertErrorWithoutSave(() -> service.unassign(command(0L)), ScheduleErrorCode.ACTION_NOT_ALLOWED);

        givenManager();
        assertErrorWithoutSave(() -> service.unassign(command(0L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.unassign(command(otherOrganizationWork.value())), ScheduleErrorCode.WORK_NOT_FOUND);
        assertErrorWithoutSave(() -> service.unassign(command(UNKNOWN_WORK_ID)), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    private void givenManager() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
    }

    private WorkId givenWork(OrganizationId organizationId, WorkStatus status) {
        Work work = Work.register(organizationId, "대상 작업", new MembershipId(1L), null, null, null);
        if (status != WorkStatus.REGISTERED) {
            work.assign(
                    new WorkSchedule(new MembershipId(3L), Instant.parse("2026-09-25T01:00:00Z"), Duration.ofHours(2)),
                    ASSIGNED_AT);
        }
        if (status == WorkStatus.ACCEPTED || status == WorkStatus.IN_PROGRESS) {
            work.accept(ASSIGNED_AT.plus(Duration.ofMinutes(30)));
        }
        if (status == WorkStatus.IN_PROGRESS) {
            work.start();
        }
        WorkId id = workRepository.save(work).id().orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private static UnassignWorkCommand command(long workId) {
        return new UnassignWorkCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), workId);
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
