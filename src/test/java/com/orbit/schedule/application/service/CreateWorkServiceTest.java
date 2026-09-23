package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.service.fake.FakeLoadActorPort;
import com.orbit.schedule.application.service.fake.FakeWorkRepository;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 등록")
class CreateWorkServiceTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    private final FakeWorkRepository workRepository = new FakeWorkRepository();
    private final FakeLoadActorPort actorPort = new FakeLoadActorPort();
    private final CreateWorkService service = new CreateWorkService(actorPort, workRepository);

    @ParameterizedTest
    @EnumSource(
            value = ActorRole.class,
            names = {"OWNER", "STAFF"})
    @DisplayName("총관리자·직원은 요청한 조직에 자신을 등록자로 작업을 등록한다")
    void registersWorkForManager(ActorRole role) {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, role);

        CreatedWorkInfo created = service.create(command("에어컨 수리", 150_000L));

        Work saved = workRepository.saved().getFirst();
        assertThat(created.workId()).isEqualTo(saved.id().orElseThrow().value());
        assertThat(saved.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.registrarId()).isEqualTo(new MembershipId(11L));
        assertThat(saved.name()).isEqualTo("에어컨 수리");
        assertThat(saved.workType()).contains(new WorkTypeId(2L));
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo("홍길동", "010-1234-5678", "서울시"));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
    }

    @Test
    @DisplayName("작업명만으로도 등록한다")
    void registersWithNameOnly() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);

        service.create(new CreateWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), "에어컨 수리", null, null, null, null, null, null));

        Work saved = workRepository.saved().getFirst();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @Test
    @DisplayName("비구성원은 입력이 잘못돼도 입력 오류가 아니라 비구성원 오류를 받는다")
    void checksMembershipBeforeInput() {
        assertErrorWithoutSave(() -> service.create(command(" ", -1L)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("기사는 입력이 잘못돼도 입력 오류가 아니라 권한 오류를 받는다")
    void checksRoleBeforeInput() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);

        assertErrorWithoutSave(() -> service.create(command(" ", -1L)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("작업 유형 식별자가 올바르지 않으면 입력 오류로 거부한다")
    void rejectsInvalidWorkTypeId() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);

        assertErrorWithoutSave(
                () -> service.create(new CreateWorkCommand(
                        ACCOUNT_ID, ORGANIZATION_ID.value(), "에어컨 수리", 0L, null, null, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("기사는 작업을 등록할 수 없다")
    void rejectsTechnician() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.TECHNICIAN);

        assertErrorWithoutSave(() -> service.create(command("에어컨 수리", null)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("요청한 조직의 활성 구성원이 아니면 등록할 수 없다")
    void rejectsNonMember() {
        actorPort.givenActor(ACCOUNT_ID, new OrganizationId(200L), 11L, ActorRole.OWNER);

        assertErrorWithoutSave(
                () -> service.create(command("에어컨 수리", null)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("작업명이 비어 있으면 입력 오류로 거부한다")
    void rejectsBlankName() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);

        assertErrorWithoutSave(() -> service.create(command(" ", null)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("요금이 음수면 입력 오류로 거부한다")
    void rejectsNegativeFee() {
        actorPort.givenActor(ACCOUNT_ID, ORGANIZATION_ID, 11L, ActorRole.STAFF);

        assertErrorWithoutSave(() -> service.create(command("에어컨 수리", -1L)), ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("조직 식별자가 올바르지 않으면 입력 오류로 거부한다")
    void rejectsInvalidOrganizationId() {
        assertErrorWithoutSave(
                () -> service.create(
                        new CreateWorkCommand(ACCOUNT_ID, 0L, "에어컨 수리", null, null, null, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static CreateWorkCommand command(String name, Long fee) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                name,
                2L,
                "홍길동",
                "010-1234-5678",
                "서울시",
                fee,
                PaymentMethod.ON_SITE_CARD);
    }

    private void assertErrorWithoutSave(Runnable call, ScheduleErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
        assertThat(workRepository.saved()).isEmpty();
    }
}
