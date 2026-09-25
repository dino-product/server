package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("작업 기본정보 수정")
class UpdateWorkDetailsServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final UpdateWorkDetailsService service =
            new UpdateWorkDetailsService(fixture.actorPort, fixture.workRepository);

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"COMPLETED", "CANCELLED"})
    @DisplayName("완료·취소 전 작업의 기본정보를 교체하고 조직·등록자·상태·배정·이력은 그대로 둔다")
    void changesDetailsBeforeTermination(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);
        Work before = fixture.stored(id);

        service.update(command(id.value(), "보일러 점검", 80_000L, 9L));

        Work saved = fixture.singleSaved();
        assertThat(saved.id()).contains(id);
        assertThat(saved.name()).isEqualTo("보일러 점검");
        assertThat(saved.workType()).contains(new WorkTypeId(9L));
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo("김철수", "010-9876-5432", "부산시"));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(80_000L), PaymentMethod.BANK_TRANSFER));
        assertThat(saved.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.registrarId()).isEqualTo(REGISTRAR_ID);
        assertThat(saved.status()).isEqualTo(status);
        assertThat(saved.schedule()).isEqualTo(before.schedule());
        assertThat(saved.assignmentHistory())
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(before.assignmentHistory());
    }

    @Test
    @DisplayName("총관리자도 기본정보를 수정한다")
    void ownerChangesDetails() {
        fixture.givenActor(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        service.update(command(id.value(), "보일러 점검", 80_000L, 9L));

        assertThat(fixture.singleSaved().name()).isEqualTo("보일러 점검");
    }

    @Test
    @DisplayName("비운 선택 항목은 현재 값을 유지하지 않고 지운다(전체 교체)")
    void clearsOmittedOptionalDetails() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        service.update(new UpdateWorkDetailsCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), id.value(), "보일러 점검", null, null, null, null, null, null));

        Work saved = fixture.singleSaved();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"COMPLETED", "CANCELLED"})
    @DisplayName("완료·취소된 작업은 상태 오류다")
    void rejectsTerminatedWork(WorkStatus status) {
        fixture.givenManager();
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", 80_000L, 9L)), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("완료된 작업이라도 어느 입력이 틀렸든 입력 오류를 상태 오류보다 먼저 알린다")
    void checksInputBeforeState() {
        fixture.givenManager();
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.assertRejected(
                () -> service.update(command(completed.value(), " ", 80_000L, 9L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(completed.value(), "보일러 점검", -1L, 9L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(completed.value(), "가".repeat(101), 80_000L, 9L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(customerCommand(completed.value(), "가".repeat(51), null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("작업명이 비었거나, 작업명·고객 정보가 길이 상한을 넘거나, 요금이 음수거나, 작업 유형 식별자가 틀리면 입력 오류다")
    void rejectsInvalidDetails() {
        fixture.givenManager();
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        fixture.assertRejected(
                () -> service.update(command(id.value(), " ", 80_000L, 9L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", -1L, 9L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", 80_000L, 0L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(id.value(), "가".repeat(101), 80_000L, 9L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(customerCommand(id.value(), "가".repeat(51), null, null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(customerCommand(id.value(), null, "0".repeat(21), null)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(customerCommand(id.value(), null, null, "가".repeat(201))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    private static UpdateWorkDetailsCommand customerCommand(long workId, String name, String phone, String address) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, "보일러 점검", null, name, phone, address, null, null);
    }

    private static UpdateWorkDetailsCommand command(long workId, String name, Long fee, Long workTypeId) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                name,
                workTypeId,
                "김철수",
                "010-9876-5432",
                "부산시",
                fee,
                PaymentMethod.BANK_TRANSFER);
    }
}
