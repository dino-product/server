package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.REGISTRAR_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

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
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

@DisplayName("작업 기본정보 수정")
class UpdateWorkDetailsServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final UpdateWorkDetailsService service =
            new UpdateWorkDetailsService(fixture.actorPort, fixture.workRepository, fixture.workTypes);

    UpdateWorkDetailsServiceTest() {
        fixture.workTypes.givenSelectable(ORGANIZATION_ID, new WorkTypeId(9L));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"REGISTERED", "PENDING_ACCEPT", "ACCEPTED"})
    @DisplayName("시작 전 작업의 기본정보를 교체하고 조직·등록자·상태·배정·이력은 그대로 둔다")
    void changesDetailsBeforeTermination(WorkStatus status) {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(status);
        Work before = fixture.stored(id);

        service.update(command(id.value(), "보일러 점검", 80_000L, 9L));

        Work saved = fixture.singleSaved();
        assertThat(saved.id()).contains(id);
        assertThat(saved.name()).isEqualTo("보일러 점검");
        assertThat(saved.workType()).contains(new WorkTypeId(9L));
        assertThat(saved.customerInfo())
                .isEqualTo(new CustomerInfo("김철수", "010-9876-5432", new SiteAddress(null, "부산시", null)));
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
    @DisplayName("총관리자는 다른 직원이 등록한 작업의 기본정보도 수정한다")
    void ownerChangesDetails() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        service.update(command(id.value(), "보일러 점검", 80_000L, 9L));

        assertThat(fixture.singleSaved().name()).isEqualTo("보일러 점검");
    }

    @Test
    @DisplayName("비운 선택 항목은 현재 값을 유지하지 않고 지운다(전체 교체)")
    void clearsOmittedOptionalDetails() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

        service.update(new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                id.value(),
                "보일러 점검",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        Work saved = fixture.singleSaved();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @Test
    @DisplayName("현장 사진을 보낸 식별자로 교체하고, 비우면 모두 지운다")
    void replacesSitePhotos() {
        fixture.givenManager(ActorRole.OWNER);
        Work work = fixture.stored(fixture.givenWork(WorkStatus.ACCEPTED));
        work.replaceSitePhotos(List.of("site-1", "site-2"));
        WorkId id = fixture.workRepository.store(work);

        service.update(photoCommand(id.value(), List.of("site-3", "site-1")));
        assertThat(fixture.stored(id).sitePhotos()).containsExactly("site-3", "site-1");

        service.update(photoCommand(id.value(), null));
        assertThat(fixture.stored(id).sitePhotos()).isEmpty();
    }

    @Test
    @DisplayName("현장 사진이 6장을 넘으면 상태·작업 유형 확인보다 먼저 입력 오류이고 바꾸지 않는다")
    void rejectsTooManySitePhotosBeforeState() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.assertRejected(
                () -> service.update(photoCommand(completed.value(), Collections.nCopies(7, "site"))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.workTypes.checks()).isZero();
    }

    private static UpdateWorkDetailsCommand photoCommand(long workId, List<String> sitePhotoIds) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                "보일러 점검",
                WORK_TYPE_ID.value(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sitePhotoIds);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkStatus.class,
            names = {"COMPLETED", "CANCELED"})
    @DisplayName("완료·취소된 작업은 상태 오류다")
    void rejectsTerminatedWork(WorkStatus status) {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(status);

        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", 80_000L, 9L)), ScheduleErrorCode.INVALID_WORK_STATE);
    }

    @Test
    @DisplayName("완료된 작업이라도 어느 입력이 틀렸든 입력 오류를 상태 오류보다 먼저 알린다")
    void checksInputBeforeState() {
        fixture.givenManager(ActorRole.OWNER);
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
        fixture.givenManager(ActorRole.OWNER);
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

    @Test
    @DisplayName("작업 유형이 요청 조직에서 선택할 수 없는 유형이면 저장하지 않고 선택할 수 없는 작업 유형 오류다")
    void rejectsWorkTypeNotSelectable() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);
        fixture.workTypes.givenSelectable(OTHER_ORGANIZATION_ID, new WorkTypeId(10L));

        for (long workTypeId : new long[] {10L, 11L}) {
            fixture.assertRejected(
                    () -> service.update(command(id.value(), "보일러 점검", 80_000L, workTypeId)),
                    ScheduleErrorCode.WORK_TYPE_NOT_SELECTABLE);
        }
        assertThat(fixture.stored(id).name()).isEqualTo("대상 작업");
    }

    @Test
    @DisplayName("작업중 작업은 지금 유형을 그대로 보내면 선택 가능 여부를 다시 확인하지 않고 수정하며, 다른 유형으로 바꾸면 상태 오류다")
    void keepsWorkTypeOfStartedWork() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
        Work before = fixture.stored(id);
        // 그 사이 지금 유형이 삭제돼 더는 고를 수 없게 된 경우
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);

        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", 80_000L, 9L)), ScheduleErrorCode.INVALID_WORK_STATE);
        fixture.assertRejected(
                () -> service.update(command(id.value(), "보일러 점검", 80_000L, null)),
                ScheduleErrorCode.INVALID_WORK_STATE);
        service.update(command(id.value(), "보일러 점검", 80_000L, WORK_TYPE_ID.value()));

        Work saved = fixture.singleSaved();
        assertThat(saved.name()).isEqualTo("보일러 점검");
        assertThat(saved.workType()).contains(WORK_TYPE_ID);
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(80_000L), PaymentMethod.BANK_TRANSFER));
        assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(saved.schedule()).isEqualTo(before.schedule());
        assertThat(saved.assignmentHistory())
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(before.assignmentHistory());
        assertThat(fixture.workTypes.checks()).isZero();
    }

    @Test
    @DisplayName("시작 전 작업도 유형이 바뀔 때만 선택 가능 여부를 확인한다")
    void checksWorkTypeOnlyWhenChanged() {
        fixture.givenManager(ActorRole.OWNER);
        WorkId id = fixture.givenWork(WorkStatus.REGISTERED);
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);

        service.update(command(id.value(), "보일러 점검", 80_000L, WORK_TYPE_ID.value()));
        assertThat(fixture.workTypes.checks()).isZero();

        service.update(command(id.value(), "보일러 점검", 80_000L, 9L));
        assertThat(fixture.workTypes.checks()).isEqualTo(1);
        assertThat(fixture.stored(id).workType()).contains(new WorkTypeId(9L));
    }

    @Test
    @DisplayName("작업 유형은 입력·상태 확인을 통과했고 유형이 바뀐 요청만 확인한다")
    void checksWorkTypeOnlyAfterInputAndStateWhenGiven() {
        fixture.givenManager(ActorRole.OWNER);
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, new WorkTypeId(9L));
        WorkId registered = fixture.givenWork(WorkStatus.REGISTERED);
        WorkId completed = fixture.givenWork(WorkStatus.COMPLETED);

        fixture.assertRejected(
                () -> service.update(command(registered.value(), " ", 80_000L, 9L)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.update(command(completed.value(), "보일러 점검", 80_000L, 9L)),
                ScheduleErrorCode.INVALID_WORK_STATE);
        service.update(command(registered.value(), "보일러 점검", 80_000L, null));

        assertThat(fixture.singleSaved().workType()).isEmpty();
        assertThat(fixture.workTypes.checks()).isZero();
    }

    private static UpdateWorkDetailsCommand customerCommand(long workId, String name, String phone, String address) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                "보일러 점검",
                null,
                name,
                phone,
                null,
                address,
                null,
                null,
                null,
                null);
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
                null,
                "부산시",
                null,
                fee,
                PaymentMethod.BANK_TRANSFER,
                null);
    }
}
