package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 기본정보 수정")
class UpdateWorkDetailsServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final WorkSchedule SCHEDULE =
            new WorkSchedule(new MembershipId(3L), NOW.plus(Duration.ofDays(1)), Duration.ofHours(2));
    private static final long UNKNOWN_WORK_ID = 999L;
    private static final long INVALID_WORK_ID = 0L;

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final UpdateWorkDetailsService service = new UpdateWorkDetailsService(actorPort, workRepository);

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"COMPLETED", "CANCELLED"})
    @DisplayName("완료·취소 전 작업의 기본정보를 교체하고 조직·등록자·상태·배정은 그대로 둔다")
    void changesDetailsBeforeTermination(WorkStatus status) {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
        Work before = givenWork(ORGANIZATION_ID, status);
        WorkId id = before.id().orElseThrow();

        service.update(validCommand(id.value()));

        assertThat(workRepository.saved()).hasSize(1);
        Work saved = workRepository.saved().getFirst();
        assertThat(saved.id()).contains(id);
        assertThat(saved.name()).isEqualTo("보일러 점검");
        assertThat(saved.workType()).contains(new WorkTypeId(9L));
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo("김철수", "010-9876-5432", "부산시"));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(80_000L), PaymentMethod.BANK_TRANSFER));
        assertThat(saved.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.registrarId()).isEqualTo(REGISTRAR_ID);
        assertThat(saved.status()).isEqualTo(status);
        assertThat(saved.schedule()).isEqualTo(before.schedule());
        assertThat(saved.assignmentHistory()).hasSameSizeAs(before.assignmentHistory());
    }

    @Test
    @DisplayName("총관리자도 기본정보를 수정한다")
    void ownerChangesDetails() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.OWNER);
        WorkId id = givenWork(ORGANIZATION_ID, WorkStatus.REGISTERED).id().orElseThrow();

        service.update(validCommand(id.value()));

        assertThat(workRepository.saved()).singleElement().satisfies(work -> assertThat(work.name())
                .isEqualTo("보일러 점검"));
    }

    @Test
    @DisplayName("비운 선택 항목은 현재 값을 유지하지 않고 지운다(전체 교체)")
    void clearsOmittedOptionalDetails() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
        WorkId id = givenWork(ORGANIZATION_ID, WorkStatus.REGISTERED).id().orElseThrow();

        service.update(new UpdateWorkDetailsCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), "보일러 점검", null, null, null, null, null, null));

        Work saved = workRepository.saved().getFirst();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @ParameterizedTest
    @ValueSource(longs = {INVALID_WORK_ID, UNKNOWN_WORK_ID})
    @DisplayName("비구성원은 작업 식별자·입력이 잘못되거나 없는 작업이어도 비구성원 오류를 받는다")
    void checksMembershipBeforeLookupAndInput(long workId) {
        givenWork(ORGANIZATION_ID, WorkStatus.REGISTERED);

        assertErrorWithoutSave(
                () -> service.update(invalidInputCommand(workId)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("기사는 다른 조직·없는·잘못된 작업 식별자와 잘못된 입력이어도 권한 오류를 받는다")
    void checksRoleBeforeLookupAndInput() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);
        WorkId otherOrganizationWork =
                givenWork(OTHER_ORGANIZATION_ID, WorkStatus.COMPLETED).id().orElseThrow();

        for (long workId : new long[] {otherOrganizationWork.value(), UNKNOWN_WORK_ID, INVALID_WORK_ID}) {
            assertErrorWithoutSave(
                    () -> service.update(invalidInputCommand(workId)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "COMPLETED", "CANCELLED"})
    @DisplayName("다른 조직의 작업은 상태·입력과 관계없이 존재를 드러내지 않고 찾을 수 없음으로 처리한다")
    void hidesWorkOfOtherOrganization(WorkStatus status) {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.OWNER);
        WorkId otherOrganizationWork =
                givenWork(OTHER_ORGANIZATION_ID, status).id().orElseThrow();

        assertErrorWithoutSave(
                () -> service.update(invalidInputCommand(otherOrganizationWork.value())),
                ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 작업은 입력이 잘못돼도 찾을 수 없음으로 처리한다")
    void rejectsUnknownWork() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.OWNER);

        assertErrorWithoutSave(
                () -> service.update(invalidInputCommand(UNKNOWN_WORK_ID)), ScheduleErrorCode.WORK_NOT_FOUND);
    }

    @Test
    @DisplayName("작업 식별자 형식이 올바르지 않으면 입력 오류로 거부한다")
    void rejectsInvalidWorkId() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.OWNER);

        assertErrorWithoutSave(
                () -> service.update(validCommand(INVALID_WORK_ID)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"COMPLETED", "CANCELLED"})
    @DisplayName("완료·취소된 작업은 상태 오류로 거부한다")
    void rejectsTerminatedWork(WorkStatus status) {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
        WorkId id = givenWork(ORGANIZATION_ID, status).id().orElseThrow();

        assertErrorWithoutSave(() -> service.update(validCommand(id.value())), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("작업명이 비어 있거나 요금이 음수면 입력 오류로 거부한다")
    void rejectsInvalidDetails() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);
        WorkId id = givenWork(ORGANIZATION_ID, WorkStatus.REGISTERED).id().orElseThrow();

        assertErrorWithoutSave(
                () -> service.update(commandWith(id.value(), " ", 80_000L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertErrorWithoutSave(
                () -> service.update(commandWith(id.value(), "보일러 점검", -1L)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private Work givenWork(OrganizationId organizationId, WorkStatus status) {
        Work work = Work.register(
                organizationId,
                "에어컨 수리",
                REGISTRAR_ID,
                new WorkTypeId(2L),
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        switch (status) {
            case REGISTERED -> {}
            case PENDING_ACCEPTANCE -> work.assign(SCHEDULE, NOW);
            case ACCEPTED -> {
                work.assign(SCHEDULE, NOW);
                work.accept(NOW);
            }
            case IN_PROGRESS -> {
                work.assign(SCHEDULE, NOW);
                work.accept(NOW);
                work.start();
            }
            case COMPLETED -> {
                work.assign(SCHEDULE, NOW);
                work.accept(NOW);
                work.start();
                work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null));
            }
            case CANCELLED -> work.cancel(NOW);
            default -> throw new IllegalArgumentException("unsupported fixture status: " + status);
        }
        Work saved = workRepository.save(work);
        workRepository.clearSaveHistory();
        return saved;
    }

    private static UpdateWorkDetailsCommand validCommand(long workId) {
        return commandWith(workId, "보일러 점검", 80_000L);
    }

    private static UpdateWorkDetailsCommand invalidInputCommand(long workId) {
        return commandWith(workId, " ", -1L);
    }

    private static UpdateWorkDetailsCommand commandWith(long workId, String name, Long fee) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                name,
                9L,
                "김철수",
                "010-9876-5432",
                "부산시",
                fee,
                PaymentMethod.BANK_TRANSFER);
    }

    private void assertErrorWithoutSave(Runnable call, ScheduleErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
        assertThat(workRepository.saved()).isEmpty();
    }
}
