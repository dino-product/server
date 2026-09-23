package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult.ConflictingWork;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 배정")
class AssignWorkServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant TEN = Instant.parse("2026-09-25T01:00:00Z");
    private static final long UNKNOWN_WORK_ID = 999L;

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final AssignWorkService service =
            new AssignWorkService(actorPort, workRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("겹치는 일정이 없으면 기사·일정을 배정해 수락대기로 만들고 배정 시각을 기록한다")
    void assignsWithoutConflict() {
        givenManager();
        WorkId id = givenRegisteredWork(ORGANIZATION_ID);

        ScheduleChangeResult result = service.assign(command(id.value(), TEN, Duration.ofHours(2), false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)));
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.assignedAt()).isEqualTo(NOW);
            assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        });
    }

    @Test
    @DisplayName("같은 기사의 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고 겹친 작업을 시작시각 순으로 돌려준다")
    void returnsConflictsWithoutApplyingWhenNotConfirmed() {
        givenManager();
        WorkId later = givenActiveWork("늦은 작업", TEN.plus(Duration.ofHours(1)));
        WorkId earlier = givenActiveWork("이른 작업", TEN.minus(Duration.ofHours(1)));
        WorkId target = givenRegisteredWork(ORGANIZATION_ID);

        ScheduleChangeResult result = service.assign(command(target.value(), TEN, Duration.ofHours(2), false));

        assertThat(result.applied()).isFalse();
        Instant earlierStart = TEN.minus(Duration.ofHours(1));
        Instant laterStart = TEN.plus(Duration.ofHours(1));
        assertThat(result.conflicts())
                .extracting(
                        ConflictingWork::workId,
                        ConflictingWork::name,
                        ConflictingWork::startTime,
                        ConflictingWork::endTime)
                .containsExactly(
                        Tuple.tuple(earlier.value(), "이른 작업", earlierStart, earlierStart.plus(Duration.ofHours(2))),
                        Tuple.tuple(later.value(), "늦은 작업", laterStart, laterStart.plus(Duration.ofHours(2))));
        assertThat(workRepository.saved()).isEmpty();
        assertThat(workRepository.findById(target).orElseThrow().status()).isEqualTo(WorkStatus.REGISTERED);
    }

    @Test
    @DisplayName("겹침을 확인했으면 배정을 반영하고 겹친 작업도 함께 돌려준다")
    void appliesWhenConflictConfirmed() {
        givenManager();
        WorkId existing = givenActiveWork("기존 작업", TEN);
        WorkId target = givenRegisteredWork(ORGANIZATION_ID);

        ScheduleChangeResult result = service.assign(command(target.value(), TEN, Duration.ofHours(2), true));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        Work saved = singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)));
    }

    @Test
    @DisplayName("다른 조직에서 같은 기사 식별자로 잡힌 작업은 겹침으로 보지 않는다")
    void ignoresWorksOfOtherOrganization() {
        givenManager();
        Work otherOrganizationWork =
                Work.register(OTHER_ORGANIZATION_ID, "다른 조직 작업", new MembershipId(1L), null, null, null);
        otherOrganizationWork.assign(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)), NOW);
        workRepository.save(otherOrganizationWork);
        WorkId target = givenRegisteredWork(ORGANIZATION_ID);

        ScheduleChangeResult result = service.assign(command(target.value(), TEN, Duration.ofHours(2), false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("거절돼 대기함으로 돌아온 작업은 다시 배정해 이력을 이어 쌓는다")
    void reassignsRejectedWork() {
        givenManager();
        WorkId id = givenRejectedWork(NOW.minus(Duration.ofHours(1)));

        service.assign(command(id.value(), TEN, Duration.ofHours(2), false));

        assertThat(singleSaved().assignmentHistory())
                .extracting(history -> history.result())
                .containsExactly(AssignmentResult.REJECTED, AssignmentResult.PENDING);
    }

    @Test
    @DisplayName("배정 시각이 직전 배정 이력보다 앞서면 입력 오류로 거부한다")
    void rejectsAssignmentBeforeLatestHistory() {
        givenManager();
        WorkId id = givenRejectedWork(NOW.plus(Duration.ofHours(1)));

        assertErrorWithoutSave(
                () -> service.assign(command(id.value(), TEN, Duration.ofHours(2), true)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("일정 입력이 잘못되면 겹치는 작업이 있어도 경고보다 먼저 입력 오류로 거부한다")
    void rejectsInvalidInputBeforeConflictWarning() {
        givenManager();
        givenActiveWork("기존 작업", TEN);
        WorkId target = givenRegisteredWork(ORGANIZATION_ID);

        assertErrorWithoutSave(
                () -> service.assign(command(target.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("배정할 수 없는 상태면 겹침 경고보다 먼저 상태 오류로 거부한다")
    void rejectsInvalidStateBeforeConflictWarning() {
        givenManager();
        givenActiveWork("기존 작업", TEN);
        WorkId alreadyAssigned = givenActiveWork("이미 배정된 작업", TEN.plus(Duration.ofDays(3)));

        assertErrorWithoutSave(
                () -> service.assign(command(alreadyAssigned.value(), TEN, Duration.ofHours(2), false)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("비구성원·기사는 작업 존재나 입력 오류보다 먼저 권한 오류를 받는다")
    void checksPermissionBeforeLookupAndInput() {
        WorkId otherOrganizationWork = givenRegisteredWork(OTHER_ORGANIZATION_ID);
        assertErrorWithoutSave(
                () -> service.assign(command(otherOrganizationWork.value(), null, Duration.ZERO, false)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);

        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);
        assertErrorWithoutSave(
                () -> service.assign(command(UNKNOWN_WORK_ID, null, Duration.ZERO, false)),
                ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("다른 조직·없는 작업은 존재를 드러내지 않고 찾을 수 없음으로 처리한다")
    void hidesWorkOfOtherOrganization() {
        givenManager();
        WorkId otherOrganizationWork = givenRegisteredWork(OTHER_ORGANIZATION_ID);

        assertErrorWithoutSave(
                () -> service.assign(command(otherOrganizationWork.value(), TEN, Duration.ofHours(2), true)),
                ScheduleErrorCode.WORK_NOT_FOUND);
        assertErrorWithoutSave(
                () -> service.assign(command(UNKNOWN_WORK_ID, TEN, Duration.ofHours(2), true)),
                ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 작업은 일정 입력이 잘못돼도 찾을 수 없음으로 처리한다")
    void checksWorkExistenceBeforeScheduleInput() {
        givenManager();

        assertErrorWithoutSave(
                () -> service.assign(command(UNKNOWN_WORK_ID, null, Duration.ZERO, false)),
                ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("작업 식별자 형식이 올바르지 않으면 입력 오류로 거부한다")
    void rejectsInvalidWorkId() {
        givenManager();

        assertErrorWithoutSave(
                () -> service.assign(command(0L, TEN, Duration.ofHours(2), false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.assign(new AssignWorkCommand(
                        ACCOUNT_ID,
                        ORGANIZATION_ID.value(),
                        null,
                        TECHNICIAN_ID.value(),
                        TEN,
                        Duration.ofHours(2),
                        false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("시작시각·소요시간이 없거나 소요시간이 양수가 아니거나 기사 식별자가 틀리면 입력 오류로 거부한다")
    void rejectsInvalidScheduleInput() {
        givenManager();
        WorkId id = givenRegisteredWork(ORGANIZATION_ID);

        assertErrorWithoutSave(
                () -> service.assign(command(id.value(), null, Duration.ofHours(2), false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.assign(command(id.value(), TEN, null, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.assign(command(id.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.assign(command(id.value(), TEN, Duration.ofHours(-1), false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        for (Long technicianId : new Long[] {0L, null}) {
            assertErrorWithoutSave(
                    () -> service.assign(new AssignWorkCommand(
                            ACCOUNT_ID,
                            ORGANIZATION_ID.value(),
                            id.value(),
                            technicianId,
                            TEN,
                            Duration.ofHours(2),
                            false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    private void givenManager() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
    }

    private WorkId givenRegisteredWork(OrganizationId organizationId) {
        WorkId id = workRepository
                .save(Work.register(organizationId, "대상 작업", new MembershipId(1L), null, null, null))
                .id()
                .orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private WorkId givenActiveWork(String name, Instant startTime) {
        Work work = Work.register(ORGANIZATION_ID, name, new MembershipId(1L), null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, startTime, Duration.ofHours(2)), NOW);
        WorkId id = workRepository.save(work).id().orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private WorkId givenRejectedWork(Instant rejectedAt) {
        Work work = Work.register(ORGANIZATION_ID, "거절된 작업", new MembershipId(1L), null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, Duration.ofHours(2)), rejectedAt);
        work.reject(RejectionReason.OTHER, rejectedAt);
        WorkId id = workRepository.save(work).id().orElseThrow();
        workRepository.clearSaveHistory();
        return id;
    }

    private static AssignWorkCommand command(long workId, Instant startTime, Duration duration, boolean confirmed) {
        return new AssignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, TECHNICIAN_ID.value(), startTime, duration, confirmed);
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
