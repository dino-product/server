package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
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
import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 기존 작업을 바꾸는 유즈케이스가 공통 오류 순서(schedule 지침)의 앞부분 — 계정 → 조직 식별자 → 구성원 → 역할 → 작업 식별자 → 작업 조회 — 을 같게 지키는지
 * 확인한다. 유즈케이스별 규칙은 각 서비스 테스트가 다룬다.
 */
@DisplayName("작업 변경 유즈케이스 공통 오류 순서")
class ManagedWorkCommandRulesTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    static Stream<UseCase> useCases() {
        return Stream.of(
                new UseCase("기본정보 수정", (f, request) -> new UpdateWorkDetailsService(f.actorPort, f.workRepository)
                        .update(new UpdateWorkDetailsCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.validInput() ? "보일러 점검" : " ",
                                null,
                                null,
                                null,
                                null,
                                request.validInput() ? 80_000L : -1L,
                                null))),
                new UseCase("배정", (f, request) -> new AssignWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.clock)
                        .assign(new AssignWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.validInput() ? TECHNICIAN_ID.value() : 0L,
                                TEN,
                                request.validInput() ? TWO_HOURS : Duration.ZERO,
                                false))),
                new UseCase("재배정", (f, request) -> new ReassignWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.clock)
                        .reassign(new ReassignWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.validInput() ? OTHER_TECHNICIAN_ID.value() : 0L,
                                TEN,
                                request.validInput() ? TWO_HOURS : Duration.ZERO,
                                false))),
                new UseCase("일정 변경", (f, request) -> new RescheduleWorkService(
                                f.actorPort, f.workRepository, f.scheduleLock, f.clock)
                        .reschedule(new RescheduleWorkCommand(
                                request.accountId(),
                                request.organizationId(),
                                request.workId(),
                                request.validInput() ? TEN.plus(Duration.ofHours(1)) : null,
                                TWO_HOURS,
                                false))),
                new UseCase("배정 해제", (f, request) -> new UnassignWorkService(f.actorPort, f.workRepository, f.clock)
                        .unassign(new UnassignWorkCommand(
                                request.accountId(), request.organizationId(), request.workId()))));
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId(UseCase useCase) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.PENDING_ACCEPTANCE);

        assertThatThrownBy(() -> useCase.invoke(fixture, new Request(null, ORGANIZATION_ID.value(), id.value(), true)))
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
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, organizationId, 0L, false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("요청한 조직의 구성원이 아니면 작업·입력이 잘못돼도 비구성원 오류다")
    void checksMembershipBeforeLookupAndInput(UseCase useCase) {
        fixture.actorPort.givenActor(ACCOUNT_ID, OTHER_ORGANIZATION_ID, 11L, ActorRole.OWNER);

        fixture.assertRejected(
                () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), UNKNOWN_WORK_ID, false)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("기사는 작업 식별자·존재·입력과 관계없이 권한 오류다")
    void checksRoleBeforeLookupAndInput(UseCase useCase) {
        fixture.givenActor(ActorRole.TECHNICIAN);
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);

        for (Long workId : new Long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID, 0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, false)),
                    ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("작업 식별자가 없거나 형식이 틀리면 입력 오류다")
    void rejectsInvalidWorkId(UseCase useCase) {
        fixture.givenManager();

        for (Long workId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, true)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("없거나 다른 조직의 작업은 입력이 잘못돼도 존재를 드러내지 않고 찾을 수 없음이다")
    void hidesMissingAndOtherOrganizationWork(UseCase useCase) {
        fixture.givenManager();
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.PENDING_ACCEPTANCE, TECHNICIAN_ID, TEN);

        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId, false)),
                    ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    record Request(Long accountId, Long organizationId, Long workId, boolean validInput) {}

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
