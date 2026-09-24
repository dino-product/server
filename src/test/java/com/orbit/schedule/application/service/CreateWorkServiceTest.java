package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 등록")
class CreateWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final CreateWorkService service = new CreateWorkService(fixture.actorPort, fixture.workRepository);

    @ParameterizedTest
    @EnumSource(
            value = ActorRole.class,
            names = {"OWNER", "STAFF"})
    @DisplayName("총관리자·직원은 요청한 조직에 자신을 등록자로 작업을 등록한다")
    void registersWorkForManager(ActorRole role) {
        fixture.givenActor(role);

        CreatedWorkInfo created = service.create(command("에어컨 수리", 150_000L));

        Work saved = fixture.singleSaved();
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
        fixture.givenManager();

        service.create(new CreateWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), "에어컨 수리", null, null, null, null, null, null));

        Work saved = fixture.singleSaved();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @Test
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId() {
        assertThatThrownBy(() -> service.create(new CreateWorkCommand(
                        null, ORGANIZATION_ID.value(), "에어컨 수리", null, null, null, null, null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
    }

    @Test
    @DisplayName("조직 식별자 형식이 틀리면 구성원 확인보다 먼저 입력 오류다")
    void checksOrganizationIdFirst() {
        fixture.assertRejected(
                () -> service.create(
                        new CreateWorkCommand(ACCOUNT_ID, 0L, "에어컨 수리", null, null, null, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("요청한 조직의 구성원이 아니면 입력이 잘못돼도 비구성원 오류다")
    void checksMembershipBeforeInput() {
        fixture.actorPort.givenActor(ACCOUNT_ID, OTHER_ORGANIZATION_ID, 11L, ActorRole.OWNER);

        fixture.assertRejected(() -> service.create(command(" ", -1L)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("기사는 입력이 잘못돼도 권한 오류다")
    void checksRoleBeforeInput() {
        fixture.givenActor(ActorRole.TECHNICIAN);

        fixture.assertRejected(() -> service.create(command(" ", -1L)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("작업명이 비었거나 요금이 음수거나 작업 유형 식별자가 틀리면 입력 오류다")
    void rejectsInvalidInput() {
        fixture.givenManager();

        fixture.assertRejected(() -> service.create(command(" ", null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(() -> service.create(command("에어컨 수리", -1L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.create(new CreateWorkCommand(
                        ACCOUNT_ID, ORGANIZATION_ID.value(), "에어컨 수리", 0L, null, null, null, null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("작업명·고객 이름·연락처·주소가 항목별 길이 상한을 넘으면 입력 오류다")
    void rejectsTooLongText() {
        fixture.givenManager();

        fixture.assertRejected(
                () -> service.create(command("가".repeat(101), null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.create(customerCommand("가".repeat(51), null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.create(customerCommand(null, "0".repeat(21), null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.create(customerCommand(null, null, "가".repeat(201))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("행위자 포트가 요청과 다른 조직의 행위자를 돌려주면 프로그래밍 오류로 멈추고 저장하지 않는다")
    void failsFastWhenActorBelongsToOtherOrganization() {
        LoadActorPort misbehavingPort = (accountId, organizationId) ->
                Optional.of(new Actor(new MembershipId(11L), OTHER_ORGANIZATION_ID, ActorRole.OWNER));
        CreateWorkService serviceWithMisbehavingPort = new CreateWorkService(misbehavingPort, fixture.workRepository);

        assertThatThrownBy(() -> serviceWithMisbehavingPort.create(command("에어컨 수리", null)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BusinessException.class)
                .hasMessage("actor must belong to the requested organization");
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    private static CreateWorkCommand customerCommand(String name, String phone, String address) {
        return new CreateWorkCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), "에어컨 수리", null, name, phone, address, null, null);
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
}
