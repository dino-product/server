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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기사가 요청하는 배정 수락·거절이 schedule 지침의 기사 유즈케이스 오류 순서 — 계정 → 조직 식별자 → 구성원 → 기사 역할 → 작업 식별자 → 작업 조회 → 배정된 적
 * 있는 기사 → 배정 순번 형식 → 담당 기사 본인 → 최신 배정 → 응답할 수 있는 상태 — 를 같게 지키는지 확인한다. 거절 입력(사유·메모)의 순서는 거절 서비스
 * 테스트가 다룬다.
 */
@DisplayName("기사 유즈케이스 공통 오류 순서")
class TechnicianWorkCommandRulesTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    static Stream<UseCase> useCases() {
        return Stream.of(
                new UseCase("수락", (f, request) -> new AcceptWorkService(f.actorPort, f.workRepository, f.clock)
                        .accept(new AcceptWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber()))),
                new UseCase("거절", (f, request) -> new RejectWorkService(f.actorPort, f.workRepository, f.clock)
                        .reject(new RejectWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.assignmentNumber(),
                                RejectionReason.SCHEDULE_CONFLICT,
                                null))));
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        assertThatThrownBy(() -> useCase.invoke(fixture, new Request(null, ORGANIZATION_ID.value(), id.value(), 1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
        assertThat(fixture.workRepository.saved()).isEmpty();
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
    void checksMembership(UseCase useCase) {
        fixture.actorPort.givenActor(ACCOUNT_ID, OTHER_ORGANIZATION_ID, TECHNICIAN_ID.value(), ActorRole.TECHNICIAN);

        fixture.assertRejected(
                () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), UNKNOWN_WORK_ID, 0)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("총관리자·직원은 기사를 대신해 응답할 수 없어 작업·입력과 관계없이 권한 오류다")
    void rejectsManagers(UseCase useCase) {
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);
        for (ActorRole role : new ActorRole[] {ActorRole.OWNER, ActorRole.STAFF}) {
            fixture.givenActor(role);

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
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);

        for (Long workId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 0)),
                    ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("그 작업에 한 번도 배정된 적 없는 기사에게는 순번이 틀려도 작업이 없는 것처럼 찾을 수 없음이다")
    void hidesWorkFromTechnicianNeverAssigned(UseCase useCase) {
        fixture.givenTechnician(OTHER_TECHNICIAN_ID);
        WorkId accepted = fixture.givenWork(WorkStatus.ACCEPTED);
        WorkId cancelled = fixture.givenWork(WorkStatus.CANCELLED);

        for (WorkId id : new WorkId[] {accepted, cancelled}) {
            for (Integer number : new Integer[] {1, 0}) {
                fixture.assertRejected(
                        () -> useCase.invoke(
                                fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number)),
                        ScheduleErrorCode.WORK_NOT_FOUND);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("배정 순번이 없거나 1보다 작으면 담당 여부를 보기 전에 입력 오류다")
    void rejectsInvalidAssignmentNumber(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId id = reassignedAwayWork();

        for (Integer number : new Integer[] {null, 0, -1}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("배정된 적 있는 기사라도 그 순번의 배정이 없거나 다른 기사의 배정이면 상태와 관계없이 담당 기사가 아니라는 권한 오류다")
    void rejectsNumberOfAnotherTechniciansAssignment(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId pending = reassignedAwayWork();
        Work acceptedByOther = fixture.stored(reassignedAwayWork());
        acceptedByOther.accept(ACCEPTED_AT.plusSeconds(60));
        WorkId accepted = fixture.workRepository.store(acceptedByOther);

        for (WorkId id : new WorkId[] {pending, accepted}) {
            for (int number : new int[] {2, 3}) {
                fixture.assertRejected(
                        () -> useCase.invoke(
                                fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), number)),
                        ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("기사가 본 배정이 그 뒤 재배정됐거나 시간이 바뀌었으면 새 배정의 상태와 관계없이 배정이 바뀌었다는 오류다")
    void rejectsStaleAssignment(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        WorkId reassignedId = reassignedAwayWork();
        Work acceptedByOther = fixture.stored(reassignedAwayWork());
        acceptedByOther.accept(ACCEPTED_AT.plusSeconds(60));
        WorkId acceptedByOtherId = fixture.workRepository.store(acceptedByOther);
        Work rescheduled = pendingWork("시간이 바뀐 작업");
        rescheduled.reschedule(TEN.plus(Duration.ofHours(3)), TWO_HOURS, ACCEPTED_AT, SETUP_MANAGER_ID);
        WorkId rescheduledId = fixture.workRepository.store(rescheduled);

        for (WorkId id : new WorkId[] {reassignedId, acceptedByOtherId, rescheduledId}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), 1)),
                    ScheduleErrorCode.ASSIGNMENT_CHANGED);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("최신 배정이 관리자 조치로 해제·취소됐으면 상태 오류다")
    void rejectsAssignmentEndedByManager(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);
        Work unassignedPending = pendingWork("응답 전에 해제된 작업");
        unassignedPending.unassign(ACCEPTED_AT, SETUP_MANAGER_ID);
        Work unassignedAccepted = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        unassignedAccepted.unassign(ACCEPTED_AT.plusSeconds(60), SETUP_MANAGER_ID);

        for (WorkId id : new WorkId[] {
            fixture.workRepository.store(unassignedPending),
            fixture.workRepository.store(unassignedAccepted),
            fixture.givenWork(WorkStatus.CANCELLED)
        }) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), 1)),
                    ScheduleErrorCode.INVALID_WORK_STATE);
        }
    }

    /** 기사 {@link ScheduleServiceFixture#TECHNICIAN_ID}에게 배정됐다가(1번) 다른 기사에게 재배정된(2번, 수락대기) 작업. */
    private WorkId reassignedAwayWork() {
        Work work = pendingWork("재배정된 작업");
        work.reassign(new WorkSchedule(OTHER_TECHNICIAN_ID, TEN, TWO_HOURS), ACCEPTED_AT, SETUP_MANAGER_ID);
        return fixture.workRepository.store(work);
    }

    private Work pendingWork(String name) {
        WorkId id = fixture.givenWork(ORGANIZATION_ID, name, WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);
        return fixture.stored(id);
    }

    record Request(Long accountId, Long organizationId, Long workId, Integer assignmentNumber) {}

    @FunctionalInterface
    interface Invocation {
        void invoke(ScheduleServiceFixture fixture, Request request);
    }

    record UseCase(String name, Invocation invocation) {

        void invoke(ScheduleServiceFixture fixture, Request request) {
            invocation.invoke(fixture, request);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
