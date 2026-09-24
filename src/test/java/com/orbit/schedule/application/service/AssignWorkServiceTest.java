package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
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
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo.ConflictingWork;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 배정")
class AssignWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final AssignWorkService service =
            new AssignWorkService(fixture.actorPort, fixture.workRepository, fixture.scheduleLock, fixture.clock);

    @Test
    @DisplayName("겹치는 일정이 없으면 기사·일정을 배정해 수락대기로 만들고, 기사를 잠근 뒤 판정하며 배정 시각을 기록한다")
    void assignsWithoutConflict() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result = service.assign(command(id.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.assignedAt()).isEqualTo(NOW);
            assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
        });
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("같은 기사의 활성 작업과 겹치는데 확인하지 않았으면 반영하지 않고 겹친 작업을 시작시각 순으로 돌려준다")
    void returnsConflictsWithoutApplyingWhenNotConfirmed() {
        fixture.givenManager();
        Instant earlierStart = TEN.minus(Duration.ofHours(1));
        Instant laterStart = TEN.plus(Duration.ofHours(1));
        WorkId earlier = activeWork("이른 작업", earlierStart);
        WorkId later = activeWork("늦은 작업", laterStart);
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result =
                service.assign(command(target.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.applied()).isFalse();
        assertThat(result.conflicts())
                .extracting(
                        ConflictingWork::workId,
                        ConflictingWork::name,
                        ConflictingWork::startTime,
                        ConflictingWork::endTime)
                .containsExactly(
                        Tuple.tuple(earlier.value(), "이른 작업", earlierStart, earlierStart.plus(TWO_HOURS)),
                        Tuple.tuple(later.value(), "늦은 작업", laterStart, laterStart.plus(TWO_HOURS)));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.stored(target).status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("시작시각이 같은 겹친 작업은 식별자 순으로 돌려준다")
    void ordersConflictsStartingTogetherByWorkId() {
        fixture.givenManager();
        WorkId first = activeWork("먼저 등록한 작업", TEN);
        WorkId second = activeWork("나중에 등록한 작업", TEN);
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result =
                service.assign(command(target.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.conflicts())
                .extracting(ConflictingWork::workId)
                .containsExactly(first.value(), second.value());
    }

    @Test
    @DisplayName("겹침을 확인했으면 배정을 반영하고 겹친 작업도 함께 돌려준다")
    void appliesWhenConflictConfirmed() {
        fixture.givenManager();
        WorkId existing = activeWork("기존 작업", TEN);
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result =
                service.assign(command(target.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, true));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).extracting(ConflictingWork::workId).containsExactly(existing.value());
        Work saved = fixture.singleSaved();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
    }

    @Test
    @DisplayName("다른 조직에서 같은 기사 식별자로 잡힌 작업은 겹침으로 보지 않는다")
    void ignoresWorksOfOtherOrganization() {
        fixture.givenManager();
        fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);

        ScheduleChangeInfo result =
                service.assign(command(target.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("같은 기사의 앞선 변경을 기다리다 잠금에 실패하면 기사 일정을 읽지도 저장하지도 않고 변경 중 오류다")
    void reportsBusyTechnicianWithoutReadingOrSaving() {
        fixture.givenManager();
        WorkId target = fixture.givenWork(WorkStatus.REGISTERED);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(
                () -> service.assign(command(target.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
        assertThat(fixture.workRepository.activeByTechnicianQueryCount()).isZero();
    }

    @Test
    @DisplayName("거절돼 대기함으로 돌아온 작업은 다시 배정해 이력을 이어 쌓는다")
    void assignsAgainAfterRejection() {
        fixture.givenManager();
        WorkId id = rejectedWork(NOW.minus(Duration.ofHours(1)));

        service.assign(command(id.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(fixture.singleSaved().assignmentHistory())
                .extracting(AssignmentHistory::result)
                .containsExactly(AssignmentResult.REJECTED, AssignmentResult.PENDING);
    }

    @Test
    @DisplayName("배정 시각이 직전 배정 이력보다 앞서면 입력 오류다")
    void rejectsAssignmentBeforeLatestHistory() {
        fixture.givenManager();
        WorkId id = rejectedWork(NOW.plus(Duration.ofHours(1)));

        fixture.assertRejected(
                () -> service.assign(command(id.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, true)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @ParameterizedTest
    @EnumSource(value = WorkStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "REGISTERED")
    @DisplayName("대기함이 아닌 작업은 겹침 경고보다 먼저 상태 오류다")
    void rejectsInvalidStateBeforeConflictWarning(WorkStatus status) {
        fixture.givenManager();
        activeWork("기존 작업", TEN);
        WorkId notInBacklog =
                fixture.givenWork(ORGANIZATION_ID, "대기함 밖 작업", status, TECHNICIAN_ID, TEN.plus(Duration.ofDays(3)));

        fixture.assertRejected(
                () -> service.assign(command(notInBacklog.value(), TECHNICIAN_ID.value(), TEN, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("일정 입력이 잘못되면 상태 오류나 겹침 경고보다 먼저 입력 오류다")
    void checksInputBeforeStateAndConflict() {
        fixture.givenManager();
        activeWork("기존 작업", TEN);
        WorkId registered = fixture.givenWork(WorkStatus.REGISTERED);
        WorkId alreadyAssigned = activeWork("이미 배정된 작업", TEN.plus(Duration.ofDays(3)));

        fixture.assertRejected(
                () -> service.assign(command(registered.value(), TECHNICIAN_ID.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.assign(
                        command(alreadyAssigned.value(), TECHNICIAN_ID.value(), TEN, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("기사 식별자·시작시각·소요시간(0 초과 24시간 이하)이 잘못되면 입력 오류다")
    void rejectsInvalidScheduleInput() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        for (Long technicianId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> service.assign(command(id.value(), technicianId, TEN, TWO_HOURS, false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        fixture.assertRejected(
                () -> service.assign(command(id.value(), TECHNICIAN_ID.value(), null, TWO_HOURS, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofHours(-1), Duration.ofHours(25)}) {
            fixture.assertRejected(
                    () -> service.assign(command(id.value(), TECHNICIAN_ID.value(), TEN, duration, false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    private WorkId activeWork(String name, Instant start) {
        return fixture.givenWork(ORGANIZATION_ID, name, WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, start);
    }

    private WorkId rejectedWork(Instant rejectedAt) {
        Work work = Work.register(ORGANIZATION_ID, "거절된 작업", REGISTRAR_ID, null, null, null);
        work.assign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), rejectedAt);
        work.reject(RejectionReason.OTHER, rejectedAt);
        return fixture.workRepository.store(work);
    }

    private static AssignWorkCommand command(
            long workId, Long technicianId, Instant start, Duration duration, boolean confirmed) {
        return new AssignWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, technicianId, start, duration, confirmed);
    }
}
