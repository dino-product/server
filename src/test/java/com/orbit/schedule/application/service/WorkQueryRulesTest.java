package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 하나를 조회하는 유즈케이스가 공통 오류 순서(schedule 지침의 조회 순서) — 계정 → 조직 식별자 → 구성원 → 작업 식별자 → 작업 조회 → 기사의 배정 여부 — 를
 * 같게 지키는지 확인한다. 각 단계는 뒤 단계의 오류를 함께 넣어도 자기 오류가 먼저 나는지 본다.
 */
@DisplayName("작업 조회 유즈케이스 공통 오류 순서")
class WorkQueryRulesTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    static Stream<UseCase> useCases() {
        return Stream.of(
                new UseCase("작업 상세", (f, request) -> new GetWorkDetailService(
                                f.actorPort, f.workRepository, photoId -> photoId, f.clock)
                        .get(new GetWorkDetailQuery(request.accountId(), request.organizationId(), request.workId()))),
                new UseCase("완료보고", (f, request) -> new GetCompletionReportService(
                                f.actorPort, f.workRepository, photoId -> photoId)
                        .get(new GetCompletionReportQuery(
                                request.accountId(), request.organizationId(), request.workId()))));
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId(UseCase useCase) {
        assertThatThrownBy(() -> useCase.invoke(fixture, new Request(null, 0L, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("조직 식별자가 없거나 형식이 틀리면 구성원·작업 확인보다 먼저 입력 오류다")
    void checksOrganizationIdFirst(UseCase useCase) {
        for (Long organizationId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, organizationId, null)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("요청한 조직의 구성원이 아니면 작업 식별자가 틀려도 비구성원 오류다")
    void checksMemberBeforeWorkId(UseCase useCase) {
        fixture.actorPort.givenManager(ACCOUNT_ID, OTHER_ORGANIZATION_ID, 11L, ActorRole.OWNER);

        fixture.assertRejected(
                () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), null)),
                ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("작업 식별자가 없거나 형식이 틀리면 입력 오류다")
    void rejectsInvalidWorkId(UseCase useCase) {
        fixture.givenTechnician(TECHNICIAN_ID);

        for (Long workId : new Long[] {0L, null}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("없거나 다른 조직의 작업은 관리자에게도 찾을 수 없음이다")
    void hidesMissingAndOtherOrganizationWork(UseCase useCase) {
        fixture.givenManager();
        WorkId otherOrganizationWork =
                fixture.givenWork(OTHER_ORGANIZATION_ID, "다른 조직 작업", WorkStatus.COMPLETED, TECHNICIAN_ID, TEN);

        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID}) {
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), workId)),
                    ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    @ParameterizedTest
    @MethodSource("useCases")
    @DisplayName("한 번도 배정받지 않은 기사에게는 대기함·배정·완료된 작업의 존재를 숨긴다")
    void hidesWorkFromNeverAssignedTechnician(UseCase useCase) {
        fixture.givenTechnician(OTHER_TECHNICIAN_ID);

        for (WorkStatus status : new WorkStatus[] {WorkStatus.REGISTERED, WorkStatus.ACCEPTED, WorkStatus.COMPLETED}) {
            WorkId id = fixture.givenWork(status);
            fixture.assertRejected(
                    () -> useCase.invoke(fixture, new Request(ACCOUNT_ID, ORGANIZATION_ID.value(), id.value())),
                    ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    record Request(Long accountId, Long organizationId, Long workId) {}

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
