package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCEPTED_AT;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.SETUP_MANAGER_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사가 요청하는 배정 수락·거절·작업 시작·완료보고가 schedule 지침의 기사 유즈케이스 오류 순서 — 계정 → 조직 식별자 → 구성원 → 요청자 종류(기사) → 작업 식별자
 * 형식 → 기사 잠금(수락·거절·시작) → 작업 조회 → 기사가 볼 수 있는 작업 → 배정 순번 형식 → 담당 기사 본인 → 최신 배정 → 처리할 수 있는 상태 — 를 같게
 * 지키는지 확인한다. 작업 식별자 형식까지의 거부는 기사를 잠그지 않고, 작업 조회부터의 거부는 수락·거절·시작이면 요청한 기사를 정확히 한 번 잠근 뒤(완료보고는
 * 잠그지 않음)임을 함께 본다. 대기 한도 초과, 거절 입력(사유·메모)과 완료보고 입력의 순서는 각 서비스 테스트가 다룬다.
 */
@DisplayName("기사 유즈케이스 공통 오류 순서")
class TechnicianWorkCommandRulesTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    static Stream<UseCase> useCases() {
        return Stream.of(
                new UseCase("수락", true, (f, request) -> new AcceptWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.events, f.clock)
                        .accept(new AcceptWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber()))),
                new UseCase("거절", true, (f, request) -> new RejectWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.events, f.clock)
                        .reject(new RejectWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber(),
                                RejectionReason.SCHEDULE_CONFLICT,
                                null))),
                new UseCase("시작", true, (f, request) -> new StartWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.events, f.clock)
                        .start(new StartWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber()))),
                new UseCase("완료보고", false, (f, request) -> new SubmitCompletionReportService(
                                f.actorPort, f.workRepository, f.events, f.clock)
                        .submit(new SubmitCompletionReportCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))));
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        assertThatThrownBy(() -> useCase.invoke(fixture, new Request(null, ORGANIZATION_ID.value(), id.value(), 1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("조직 식별자가 없거나 형식이 틀리면 구성원 확인보다 먼저 입력 오류다")
    void checksOrganizationIdFirst(UseCase useCase) {
        for (Long organizationId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, organizationId, 0L, 0)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("요청한 조직의 구성원이 아니면 작업·입력이 잘못돼도 비구성원 오류다")
    void checksActiveActor(UseCase useCase) {
        fixture.actorPort.givenTechnician(ACCOUNT_ID, OTHER_ORGANIZATION_ID, TECHNICIAN_ID.value());

        fixture.assertRejected(
                () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), UNKNOWN_WORK_ID, 0)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("총관리자·직원은 기사를 대신해 처리할 수 없어 작업·입력과 관계없이 권한 오류다")
    void rejectsManagers(UseCase useCase) {
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPT);
        for (ActorRole role : new ActorRole[] {ActorRole.OWNER, ActorRole.STAFF}) {
            fixture.givenManager(role);

            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), 1)),
                    ScheduleErrorCode.ACTION_NOT_ALLOWED);
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), 0L, 0)),
                    ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("작업 식별자가 없거나 틀리면 입력 오류, 없거나 다른 조직의 작업은 순번이 틀려도 찾을 수 없음이다")
    void checksWorkIdThenLookup(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN);

        for (Long workId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID}) {
            assertRejectedAfterLookup(
                    useCase,
                    new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 0),
                    ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("작업 식별자 형식이 틀리면 기사 잠금이 대기 한도를 넘기는 중이어도 잠그기 전에 입력 오류다")
    void checksWorkIdFormatBeforeLock(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        for (Long workId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("배정받은 적 없거나, 거절했거나, 다른 기사로 재배정됐거나, 배정 해제된 작업은 순번이 틀려도 작업이 없는 것처럼 찾을 수 없음이다")
    void hidesWorkTechnicianCannotSee(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work rejected = pendingWork("거절한 작업");
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        Work rejectedThenCancelled = pendingWork("거절한 뒤 대기함에서 취소된 작업");
        rejectedThenCancelled.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), ACCEPTED_AT);
        rejectedThenCancelled.cancel(
                ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        Work acceptedByOther = fixture.stored(reassignedAwayWork());
        acceptedByOther.accept(ACCEPTED_AT.plusSeconds(60));
        Work unassignedPending = pendingWork("응답 전에 해제된 작업");
        unassignedPending.unassign(ACCEPTED_AT, SETUP_MANAGER_ID);
        Work unassignedAccepted = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        unassignedAccepted.unassign(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);

        for (WorkId id : new WorkId[] {
            fixture.givenWork(WorkStatus.REGISTERED),
            fixture.givenWork(ORGANIZATION_ID, "다른 기사 작업", WorkStatus.ACCEPTED, OTHER_TECHNICIAN_ID, TEN),
            fixture.givenWork(ORGANIZATION_ID, "다른 기사의 취소 작업", WorkStatus.CANCELED, OTHER_TECHNICIAN_ID, TEN),
            fixture.workRepository.store(rejected),
            fixture.workRepository.store(rejectedThenCancelled),
            reassignedAwayWork(),
            fixture.workRepository.store(acceptedByOther),
            fixture.workRepository.store(unassignedPending),
            fixture.workRepository.store(unassignedAccepted)
        }) {
            for (Integer number : new Integer[] {1, 0}) {
                assertRejectedAfterLookup(
                        useCase,
                        new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number),
                        ScheduleErrorCode.WORK_NOT_FOUND);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("배정 순번이 없거나 1보다 작으면 담당 여부를 보기 전에 입력 오류다")
    void rejectsInvalidAssignmentNumber(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = reassignedInWork();

        for (Integer number : new Integer[] {null, 0, -1}) {
            assertRejectedAfterLookup(
                    useCase,
                    new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("볼 수 있는 작업이라도 그 순번의 배정이 없거나 다른 기사의 배정이면 상태와 관계없이 담당 기사가 아니라는 권한 오류다")
    void rejectsNumberOfAnotherTechniciansAssignment(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId pending = reassignedInWork();
        Work acceptedWork = fixture.stored(reassignedInWork());
        acceptedWork.accept(ACCEPTED_AT.plusSeconds(60));
        WorkId accepted = fixture.workRepository.store(acceptedWork);

        for (WorkId id : new WorkId[] {pending, accepted}) {
            for (int number : new int[] {1, 3}) {
                assertRejectedAfterLookup(
                        useCase,
                        new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number),
                        ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("기사가 본 배정이 그 뒤 시간이 바뀌었거나 다른 기사를 거쳐 다시 배정됐으면 새 배정의 상태와 관계없이 배정이 바뀌었다는 오류다")
    void rejectsStaleAssignment(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work rescheduled = pendingWork("시간이 바뀐 작업");
        rescheduled.reschedule(TEN.plus(Duration.ofHours(3)), TWO_HOURS, ACCEPTED_AT, SETUP_MANAGER_ID);
        WorkId rescheduledId = fixture.workRepository.store(rescheduled);
        WorkId returnedId = fixture.workRepository.store(returnedWork());
        Work returnedAccepted = returnedWork();
        returnedAccepted.accept(ACCEPTED_AT.plusSeconds(120));
        WorkId returnedAcceptedId = fixture.workRepository.store(returnedAccepted);

        for (WorkId id : new WorkId[] {rescheduledId, returnedId, returnedAcceptedId}) {
            assertRejectedAfterLookup(
                    useCase,
                    new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), 1),
                    ScheduleErrorCode.ASSIGNMENT_CHANGED);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("취소된 작업은 취소 당시 담당 기사에게 보이지만 처리할 수 없어 상태 오류다")
    void rejectsCancelledWork(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work cancelledAccepted = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        cancelledAccepted.cancel(
                ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);

        for (WorkId id :
                new WorkId[] {fixture.givenWork(WorkStatus.CANCELED), fixture.workRepository.store(cancelledAccepted)
                }) {
            assertRejectedAfterLookup(
                    useCase,
                    new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), 1),
                    ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    /**
     * 작업 조회부터의 단계에서 거부되는지 본다. 저장하지 않았고, 기사를 잠그는 유즈케이스는 이 요청에서 요청한 기사를 정확히 한 번(기사의 다른 작업은 읽기 전에)
     * 잠갔으며 완료보고는 잠그지 않았는지 확인한다.
     */
    private void assertRejectedAfterLookup(UseCase useCase, Request request, ScheduleErrorCode expected) {
        int locksBefore = fixture.scheduleLock.locks().size();

        fixture.assertRejectedWithoutSaving(() -> useCase.invoke(fixture, request), expected);

        List<Lock> locks = fixture.scheduleLock.locks();
        List<Lock> expectedLocks =
                useCase.locksTechnician() ? List.of(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0)) : List.of();
        assertThat(locks.subList(locksBefore, locks.size())).isEqualTo(expectedLocks);
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}에게 배정됐다가(1번) 다른 기사에게 재배정된(2번, 수락대기) 작업. */
    private WorkId reassignedAwayWork() {
        Work work = pendingWork("재배정된 작업");
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}에게 배정됐다가(1번) 다른 기사를 거쳐(2번) 다시 그 기사에게 재배정된(3번, 수락대기) 작업. */
    private Work returnedWork() {
        Work work = fixture.stored(reassignedAwayWork());
        work.reassign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);
        return work;
    }

    /**
     * 기사 {@link ScheduleServiceFixture#OTHER_TECHNICIAN_ID}에게 배정됐다가(1번) 기사
     * {@link ScheduleServiceFixture#TECHNICIAN_ID}에게 재배정된(2번, 수락대기) 작업.
     */
    private WorkId reassignedInWork() {
        WorkId id = fixture.givenWork(ORGANIZATION_ID, "넘겨받은 작업", WorkStatus.PENDING_ACCEPT, OTHER_TECHNICIAN_ID, TEN);
        Work work = fixture.stored(id);
        work.reassign(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    private Work pendingWork(String name) {
        WorkId id = fixture.givenWork(ORGANIZATION_ID, name, WorkStatus.PENDING_ACCEPT, TECHNICIAN_ID, TEN);
        return fixture.stored(id);
    }

    record Request(Long accountId, Long organizationId, Long workId, Integer assignmentNumber) {}

    @FunctionalInterface
    interface Invocation {
        void invoke(ScheduleServiceFixture fixture, Request request);
    }

    /** {@code locksTechnician}: 작업을 읽기 전에 요청한 기사를 잠그는 유즈케이스(수락·거절·시작)인지. */
    record UseCase(String name, boolean locksTechnician, Invocation invocation) {

        void invoke(ScheduleServiceFixture fixture, Request request) {
            invocation.invoke(fixture, request);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
