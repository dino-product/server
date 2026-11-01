package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTERED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand.Cause;
import com.orbit.schedule.application.port.in.command.dto.TechnicianWorkReleaseInfo;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("기사 계약 종료의 대기함 반환")
class ReleaseTechnicianWorksServiceTest {

    private static final MembershipId PROCESSED_BY = new MembershipId(21L);

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final ReleaseTechnicianWorksService service = new ReleaseTechnicianWorksService(
            fixture.workRepository, fixture.scheduleLock, fixture.events, fixture.clock);

    @AfterEach
    void clearIsolationLevel() {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    @DisplayName("배정됨·작업전 작업을 처리한 관리자의 해제로 끝내고 대기함으로 돌린 뒤 돌린 작업을 식별자 순으로 돌려주고 작업 변경 한 건을 발행한다")
    void releasesPendingAndAcceptedWorks(Cause cause) {
        WorkId pending = givenWork(WorkStatus.PENDING_ACCEPT, 0);
        WorkId accepted = givenWork(WorkStatus.ACCEPTED, 3);

        TechnicianWorkReleaseInfo result = service.release(command(cause));

        assertThat(result).isEqualTo(TechnicianWorkReleaseInfo.released(List.of(pending.value(), accepted.value())));
        assertThat(fixture.workRepository.saved()).hasSize(2);
        Work releasedPending = fixture.stored(pending);
        assertThat(releasedPending.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(releasedPending.schedule()).isEmpty();
        assertThat(releasedPending.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW, PROCESSED_BY, AssignmentEndReason.UNASSIGNED));
        });
        Work releasedAccepted = fixture.stored(accepted);
        assertThat(releasedAccepted.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(releasedAccepted.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(ACCEPTED_AT);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW, PROCESSED_BY, AssignmentEndReason.UNASSIGNED));
        });
        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(pending.value(), accepted.value()), NOW));
    }

    @Test
    @DisplayName("비활성화는 작업중·완료 작업을 그대로 두고 배정됨·작업전 작업만 돌린다")
    void deactivationKeepsInProgressAndCompletedWorks() {
        WorkId inProgress = givenWork(WorkStatus.IN_PROGRESS, 0);
        WorkId completed = givenWork(WorkStatus.COMPLETED, 3);
        WorkId pending = givenWork(WorkStatus.PENDING_ACCEPT, 6);

        TechnicianWorkReleaseInfo result = service.release(command(Cause.DEACTIVATION));

        assertThat(result).isEqualTo(TechnicianWorkReleaseInfo.released(List.of(pending.value())));
        assertThat(fixture.workRepository.saved()).singleElement().satisfies(saved -> assertThat(saved.id())
                .contains(pending));
        assertThat(fixture.stored(inProgress).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(fixture.stored(completed).status()).isEqualTo(WorkStatus.COMPLETED);
    }

    @Test
    @DisplayName("역할 변경은 작업중 작업이 있으면 아무것도 바꾸지 않고 작업중 작업을 식별자 순으로 돌려준다")
    void roleChangeIsBlockedByInProgressWorks() {
        WorkId pending = givenWork(WorkStatus.PENDING_ACCEPT, 0);
        WorkId firstInProgress = givenWork(WorkStatus.IN_PROGRESS, 3);
        WorkId secondInProgress = givenWork(WorkStatus.IN_PROGRESS, 6);

        TechnicianWorkReleaseInfo result = service.release(command(Cause.ROLE_CHANGE));

        assertThat(result)
                .isEqualTo(
                        TechnicianWorkReleaseInfo.blocked(List.of(firstInProgress.value(), secondInProgress.value())));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        assertThat(fixture.events.published()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    @DisplayName("돌릴 예정 작업이 없으면 빈 결과로 성공하고 작업 변경을 발행하지 않는다")
    void succeedsWithoutScheduledWorks(Cause cause) {
        givenWork(WorkStatus.COMPLETED, 0);

        assertThat(service.release(command(cause))).isEqualTo(TechnicianWorkReleaseInfo.released(List.of()));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.events.published()).isEmpty();
    }

    @Test
    @DisplayName("다른 기사와 다른 조직의 작업, 미리 입력한 담당기사가 없는 대기함 작업은 건드리지 않는다")
    void leavesOtherWorksUntouched() {
        WorkId otherTechnician =
                fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN);
        WorkId otherOrganization =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.ACCEPTED, TECHNICIAN_ID, TEN);
        WorkId backlog = fixture.givenWork(WorkStatus.REGISTERED);

        assertThat(service.release(command(Cause.DEACTIVATION)))
                .isEqualTo(TechnicianWorkReleaseInfo.released(List.of()));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(otherTechnician).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.workRepository
                        .findInOrganization(OTHER_ORGANIZATION_ID, otherOrganization)
                        .orElseThrow()
                        .status())
                .isEqualTo(WorkStatus.ACCEPTED);
        assertThat(fixture.stored(backlog).status()).isEqualTo(WorkStatus.REGISTERED);
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    @DisplayName("대기함 작업이 미리 입력한 담당기사가 이 기사이면 담당기사만 비우고, 반환한 작업 목록에는 넣지 않되 작업 변경에는 담는다")
    void clearsPlannedTechnicianOfBacklogWorks(Cause cause) {
        WorkId technicianOnly = givenPlanned(ORGANIZATION_ID, new PlannedSchedule(TECHNICIAN_ID, null, null));
        WorkId withTime = givenPlanned(ORGANIZATION_ID, new PlannedSchedule(TECHNICIAN_ID, TEN, null));
        WorkId otherTechnician = givenPlanned(ORGANIZATION_ID, new PlannedSchedule(OTHER_TECHNICIAN_ID, TEN, null));
        WorkId otherOrganization = givenPlanned(OTHER_ORGANIZATION_ID, new PlannedSchedule(TECHNICIAN_ID, null, null));
        WorkId pending = givenWork(WorkStatus.PENDING_ACCEPT, 0);

        TechnicianWorkReleaseInfo result = service.release(command(cause));

        assertThat(result).isEqualTo(TechnicianWorkReleaseInfo.released(List.of(pending.value())));
        assertThat(fixture.workRepository.saved())
                .extracting(work -> work.id().orElseThrow())
                .containsExactly(pending, technicianOnly, withTime);
        assertThat(fixture.stored(technicianOnly).plannedSchedule()).isEmpty();
        assertThat(fixture.stored(withTime).plannedSchedule()).contains(new PlannedSchedule(null, TEN, null));
        assertThat(fixture.stored(otherTechnician).plannedSchedule())
                .contains(new PlannedSchedule(OTHER_TECHNICIAN_ID, TEN, null));
        assertThat(fixture.workRepository
                        .findInOrganization(OTHER_ORGANIZATION_ID, otherOrganization)
                        .orElseThrow()
                        .plannedSchedule())
                .contains(new PlannedSchedule(TECHNICIAN_ID, null, null));
        assertThat(fixture.events.published())
                .containsExactly(new WorkChanged(
                        ORGANIZATION_ID.value(),
                        List.of(pending.value(), technicianOnly.value(), withTime.value()),
                        NOW));
    }

    @Test
    @DisplayName("역할 변경이 작업중 작업으로 막히면 미리 입력한 담당기사도 그대로 둔다")
    void roleChangeBlockedKeepsPlannedTechnician() {
        WorkId planned = givenPlanned(ORGANIZATION_ID, new PlannedSchedule(TECHNICIAN_ID, null, null));
        givenWork(WorkStatus.IN_PROGRESS, 0);

        service.release(command(Cause.ROLE_CHANGE));

        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(planned).plannedSchedule()).contains(new PlannedSchedule(TECHNICIAN_ID, null, null));
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    @DisplayName("기사의 작업을 읽기 전에 그 기사를 잠가, 잠금을 기다리는 동안 커밋된 배정과 미리 입력한 등록까지 처리한다")
    void locksTechnicianBeforeReadingWorks(Cause cause) {
        WorkId[] assignedWhileWaiting = new WorkId[1];
        WorkId[] plannedWhileWaiting = new WorkId[1];
        fixture.scheduleLock.whileWaiting(() -> {
            assignedWhileWaiting[0] = givenWork(WorkStatus.PENDING_ACCEPT, 0);
            plannedWhileWaiting[0] = givenPlanned(ORGANIZATION_ID, new PlannedSchedule(TECHNICIAN_ID, null, null));
        });

        TechnicianWorkReleaseInfo result = service.release(command(cause));

        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(result).isEqualTo(TechnicianWorkReleaseInfo.released(List.of(assignedWhileWaiting[0].value())));
        assertThat(fixture.stored(plannedWhileWaiting[0]).plannedSchedule()).isEmpty();
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경을 기다리다 대기 한도를 넘기면 409이고 아무것도 바꾸지 않는다")
    void rejectsWhenTechnicianScheduleIsBusy() {
        givenWork(WorkStatus.PENDING_ACCEPT, 0);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        assertThatThrownBy(() -> service.release(command(Cause.DEACTIVATION)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY));
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("한 작업이라도 돌릴 수 없으면 관리자 입력 오류로 바꾸지 않고 도메인 예외로 실패하며 어떤 작업도 저장하지 않는다")
    void savesNothingWhenAnyWorkCannotBeReleased() {
        givenWork(WorkStatus.PENDING_ACCEPT, 0);
        Work assignedLater =
                Work.register(ORGANIZATION_ID, "나중에 배정된 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null);
        assignedLater.assign(
                new WorkSchedule(TECHNICIAN_ID, TEN.plus(Duration.ofHours(3)), TWO_HOURS),
                NOW.plus(Duration.ofHours(1)),
                PROCESSED_BY);
        fixture.workRepository.store(assignedLater);

        assertThatThrownBy(() -> service.release(command(Cause.DEACTIVATION)))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(BusinessException.class);
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_SERIALIZABLE})
    @DisplayName("바깥 트랜잭션이 READ COMMITTED보다 높은 격리 수준이면 잠근 뒤에도 앞선 커밋을 못 보므로 잠그기 전에 거부한다")
    void rejectsOuterTransactionAboveReadCommitted(int isolationLevel) {
        givenWork(WorkStatus.PENDING_ACCEPT, 0);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(isolationLevel);

        assertThatThrownBy(() -> service.release(command(Cause.DEACTIVATION)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.scheduleLock.locks()).isEmpty();
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("바깥 트랜잭션이 READ COMMITTED로 지정됐으면 그대로 돌린다")
    void acceptsOuterTransactionInReadCommitted() {
        WorkId pending = givenWork(WorkStatus.PENDING_ACCEPT, 0);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);

        assertThat(service.release(command(Cause.DEACTIVATION)))
                .isEqualTo(TechnicianWorkReleaseInfo.released(List.of(pending.value())));
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}의 작업을 {@link ScheduleServiceFixture#TEN}보다 주어진 시간 뒤에 시작하게 둔다. */
    private WorkId givenWork(WorkStatus status, int hoursAfterTen) {
        Instant start = TEN.plus(Duration.ofHours(hoursAfterTen));
        return fixture.givenWork(ORGANIZATION_ID, "기사 작업", status, TECHNICIAN_ID, start);
    }

    private WorkId givenPlanned(OrganizationId organizationId, PlannedSchedule plannedSchedule) {
        return fixture.workRepository.store(Work.register(
                organizationId, "미리 입력한 작업", REGISTRAR_ID, REGISTERED_AT, null, null, null, plannedSchedule));
    }

    private static ReleaseTechnicianWorksCommand command(Cause cause) {
        return new ReleaseTechnicianWorksCommand(
                ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), PROCESSED_BY.value(), cause);
    }
}
