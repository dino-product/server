package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.NOW;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TWO_HOURS;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.WORK_TYPE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.schedule.WorkAssigned;
import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo.ConflictingWork;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.application.service.fake.FakeLockTechnicianSchedulePort.Lock;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;

@DisplayName("작업 등록")
class CreateWorkServiceTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();
    private final CreateWorkService service = new CreateWorkService(
            fixture.actorPort,
            fixture.workRepository,
            fixture.workTypes,
            fixture.technicianContracts,
            fixture.scheduleLock,
            fixture.events,
            fixture.clock);

    @ParameterizedTest
    @EnumSource(
            value = ActorRole.class,
            names = {"OWNER", "STAFF"})
    @DisplayName("총관리자·직원은 요청한 조직에 자신을 등록자로, 지금을 등록 시각으로 작업을 등록한다")
    void registersWorkForManager(ActorRole role) {
        fixture.givenManager(role);

        CreatedWorkInfo created = service.create(command("에어컨 수리", 150_000L));

        Work saved = fixture.singleSaved();
        assertThat(created.workId()).isEqualTo(saved.id().orElseThrow().value());
        assertThat(saved.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.registrarId()).isEqualTo(new MembershipId(11L));
        assertThat(saved.registeredAt()).isEqualTo(NOW);
        assertThat(saved.name()).isEqualTo("에어컨 수리");
        assertThat(saved.workType()).contains(new WorkTypeId(2L));
        assertThat(saved.customerInfo())
                .isEqualTo(new CustomerInfo("홍길동", "010-1234-5678", new SiteAddress("04043", "서울시", "3층")));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(saved.schedule()).isEmpty();
    }

    @Test
    @DisplayName("작업명만으로도 등록한다")
    void registersWithNameOnly() {
        fixture.givenManager();

        service.create(new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false));

        Work saved = fixture.singleSaved();
        assertThat(saved.workType()).isEmpty();
        assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
    }

    @Test
    @DisplayName("계정 식별자가 없으면 인증 계층의 프로그래밍 오류로 멈춘다")
    void requiresAccountId() {
        assertThatThrownBy(() -> service.create(new CreateWorkCommand(
                        null,
                        ORGANIZATION_ID.value(),
                        "에어컨 수리",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accountId must not be null");
    }

    @Test
    @DisplayName("조직 식별자 형식이 틀리면 구성원 확인보다 먼저 입력 오류다")
    void checksOrganizationIdFirst() {
        fixture.assertRejected(
                () -> service.create(new CreateWorkCommand(
                        ACCOUNT_ID,
                        0L,
                        "에어컨 수리",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
    }

    @Test
    @DisplayName("요청한 조직의 구성원이 아니면 입력이 잘못돼도 비구성원 오류다")
    void checksActiveActorBeforeInput() {
        fixture.actorPort.givenManager(ACCOUNT_ID, OTHER_ORGANIZATION_ID, 11L, ActorRole.OWNER);

        fixture.assertRejected(() -> service.create(command(" ", -1L)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("기사는 입력이 잘못돼도 권한 오류다")
    void checksActorKindBeforeInput() {
        fixture.givenTechnician(TECHNICIAN_ID);

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
                        ACCOUNT_ID,
                        ORGANIZATION_ID.value(),
                        "에어컨 수리",
                        0L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)),
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
    @DisplayName("작업 유형이 요청 조직에서 선택할 수 없는 유형이면 저장하지 않고 선택할 수 없는 작업 유형 오류다")
    void rejectsWorkTypeNotSelectable() {
        fixture.givenManager();
        fixture.workTypes.givenSelectable(OTHER_ORGANIZATION_ID, new WorkTypeId(10L));
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);

        for (long workTypeId : new long[] {WORK_TYPE_ID.value(), 10L}) {
            fixture.assertRejected(
                    () -> service.create(new CreateWorkCommand(
                            ACCOUNT_ID,
                            ORGANIZATION_ID.value(),
                            "에어컨 수리",
                            workTypeId,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false)),
                    ScheduleErrorCode.WORK_TYPE_NOT_SELECTABLE);
        }
    }

    @Test
    @DisplayName("작업 유형은 입력 확인을 통과했고 유형을 지정한 요청만 확인한다")
    void checksWorkTypeOnlyAfterInputWhenGiven() {
        fixture.givenManager();
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);

        fixture.assertRejected(() -> service.create(command(" ", null)), ScheduleErrorCode.INVALID_WORK_INPUT);
        service.create(new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false));

        assertThat(fixture.singleSaved().workType()).isEmpty();
        assertThat(fixture.workTypes.checks()).isZero();
    }

    @Test
    @DisplayName("담당기사·시작시각·소요시간을 모두 주면 등록과 함께 배정하고, 기사를 잠근 뒤 기사 계약을 확인하며 저장한 작업의 배정을 발행한다")
    void registersAndAssigns() {
        fixture.givenManager();

        CreatedWorkInfo created = service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        Work saved = fixture.singleSaved();
        assertThat(created.workId()).isEqualTo(saved.id().orElseThrow().value());
        assertThat(created.scheduleChange().applied()).isTrue();
        assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        assertThat(saved.schedule()).contains(new WorkSchedule(TECHNICIAN_ID, TEN, TWO_HOURS));
        assertThat(saved.registeredAt()).isEqualTo(NOW);
        assertThat(saved.assignmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.assignedAt()).isEqualTo(NOW);
            assertThat(history.assignedBy()).isEqualTo(new MembershipId(11L));
        });
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.technicianContracts.checks()).isEqualTo(1);
        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkAssigned(
                                created.workId(), ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), "에어컨 수리", TEN, NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(created.workId()), NOW));
    }

    @Test
    @DisplayName("일정을 비우면 대기함에 두고 기사를 잠그거나 확인하지 않으며, 배정이 아니므로 작업 변경만 발행한다")
    void registersBacklogWithoutLock() {
        fixture.givenManager();

        CreatedWorkInfo created = service.create(command("에어컨 수리", null));

        assertThat(created.scheduleChange().applied()).isTrue();
        assertThat(created.scheduleChange().conflicts()).isEmpty();
        assertThat(fixture.singleSaved().status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(fixture.scheduleLock.locks()).isEmpty();
        assertThat(fixture.technicianContracts.checks()).isZero();
        assertThat(fixture.events.published())
                .containsExactly(new WorkChanged(ORGANIZATION_ID.value(), List.of(created.workId()), NOW));
    }

    @Test
    @DisplayName("담당기사·시작시각·소요시간 중 일부만 주면 입력한 값을 남긴 채 대기함에 두고, 미리 입력한 담당기사만 잠그며 작업 변경만 발행한다")
    void keepsPartialScheduleInBacklog() {
        fixture.givenManager();

        CreatedWorkInfo technicianOnly = service.create(scheduled(TECHNICIAN_ID.value(), null, null, false));
        CreatedWorkInfo timeOnly = service.create(scheduled(null, TEN, TWO_HOURS, false));

        assertThat(technicianOnly.scheduleChange().applied()).isTrue();
        assertThat(fixture.workRepository.saved())
                .extracting(Work::status, work -> work.plannedSchedule().orElseThrow())
                .containsExactly(
                        Tuple.tuple(WorkStatus.REGISTERED, new PlannedSchedule(TECHNICIAN_ID, null, null)),
                        Tuple.tuple(WorkStatus.REGISTERED, new PlannedSchedule(null, TEN, TWO_HOURS)));
        assertThat(timeOnly.workId()).isNotNull();
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
        assertThat(fixture.technicianContracts.checks()).isEqualTo(1);
        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(technicianOnly.workId()), NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(timeOnly.workId()), NOW));
    }

    @Test
    @DisplayName("일부만 준 담당기사는 잠근 뒤 확인해, 잠금을 기다리는 동안 끝난 기사 계약이면 저장하지 않고 배정할 수 없는 기사 오류다")
    void rejectsInactiveTechnicianOfPartialSchedule() {
        fixture.givenManager();
        fixture.scheduleLock.whileWaiting(
                () -> fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID));

        assertThatThrownBy(() -> service.create(scheduled(TECHNICIAN_ID.value(), TEN, null, false)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_NOT_ASSIGNABLE));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("일부만 준 담당기사를 잠그다 대기 한도를 넘기면 기사 계약을 확인하지도 저장하지도 않고 변경 중 오류다")
    void reportsBusyPlannedTechnician() {
        fixture.givenManager();
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID);
        fixture.scheduleLock.failWith(new TechnicianScheduleBusyException(null));

        fixture.assertRejected(
                () -> service.create(scheduled(TECHNICIAN_ID.value(), TEN, null, false)),
                ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
        assertThat(fixture.technicianContracts.checks()).isZero();
    }

    @Test
    @DisplayName("작업 유형은 일부만 준 담당기사의 기사 계약보다 먼저 확인한다")
    void checksWorkTypeBeforePlannedTechnician() {
        fixture.givenManager();
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID);

        fixture.assertRejected(
                () -> service.create(new CreateWorkCommand(
                        ACCOUNT_ID,
                        ORGANIZATION_ID.value(),
                        "에어컨 수리",
                        WORK_TYPE_ID.value(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TECHNICIAN_ID.value(),
                        null,
                        null,
                        false)),
                ScheduleErrorCode.WORK_TYPE_NOT_SELECTABLE);
        assertThat(fixture.technicianContracts.checks()).isZero();
    }

    @Test
    @DisplayName("일부만 준 값도 형식이 틀리면 기사 계약 확인보다 먼저 입력 오류다")
    void checksPartialInputBeforeTechnicianContract() {
        fixture.givenManager();
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID);

        fixture.assertRejected(
                () -> service.create(scheduled(TECHNICIAN_ID.value(), null, Duration.ZERO, false)),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        fixture.assertRejected(
                () -> service.create(scheduled(0L, null, null, false)), ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(fixture.technicianContracts.checks()).isZero();
    }

    @Test
    @DisplayName("같은 기사의 활성 작업과 겹치는데 확인하지 않았으면 작업을 만들지 않고 겹친 작업을 돌려준다")
    void doesNotCreateWhenConflictUnconfirmed() {
        fixture.givenManager();
        WorkId existing = fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        CreatedWorkInfo created = service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(created.workId()).isNull();
        assertThat(created.scheduleChange().applied()).isFalse();
        assertThat(created.scheduleChange().conflicts())
                .extracting(ConflictingWork::workId)
                .containsExactly(existing.value());
        assertThat(fixture.workRepository.saved()).isEmpty();

        CreatedWorkInfo confirmed = service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, true));

        assertThat(confirmed.workId()).isNotNull();
        assertThat(confirmed.scheduleChange().applied()).isTrue();
        assertThat(fixture.singleSaved().status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("겹침을 확인하지 않아 작업을 만들지 않았으면 발행하지 않고, 확인해 만든 뒤에만 배정 하나를 발행한다")
    void publishesAssignmentOnlyWhenCreated() {
        fixture.givenManager();
        fixture.givenWork(WorkStatus.PENDING_ACCEPT);

        service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, false));

        assertThat(fixture.events.published()).isEmpty();

        CreatedWorkInfo confirmed = service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, true));

        assertThat(fixture.events.published())
                .containsExactly(
                        new WorkAssigned(
                                confirmed.workId(), ORGANIZATION_ID.value(), TECHNICIAN_ID.value(), "에어컨 수리", TEN, NOW),
                        new WorkChanged(ORGANIZATION_ID.value(), List.of(confirmed.workId()), NOW));
    }

    @Test
    @DisplayName("배정할 기사가 활성 기사 계약이 아니면 잠근 뒤 작업을 만들지 않고 배정할 수 없는 기사 오류다")
    void rejectsInactiveTechnician() {
        fixture.givenManager();
        fixture.technicianContracts.givenInactive(ORGANIZATION_ID, TECHNICIAN_ID);

        assertThatThrownBy(() -> service.create(scheduled(TECHNICIAN_ID.value(), TEN, TWO_HOURS, true)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ScheduleErrorCode.TECHNICIAN_NOT_ASSIGNABLE));
        assertThat(fixture.workRepository.saved()).isEmpty();
        assertThat(fixture.scheduleLock.locks()).containsExactly(new Lock(ORGANIZATION_ID, TECHNICIAN_ID, 0));
    }

    @Test
    @DisplayName("작업 유형은 기사를 잠그기 전에 확인한다")
    void checksWorkTypeBeforeLock() {
        fixture.givenManager();
        fixture.workTypes.givenNotSelectable(ORGANIZATION_ID, WORK_TYPE_ID);

        fixture.assertRejected(
                () -> service.create(new CreateWorkCommand(
                        ACCOUNT_ID,
                        ORGANIZATION_ID.value(),
                        "에어컨 수리",
                        WORK_TYPE_ID.value(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TECHNICIAN_ID.value(),
                        TEN,
                        TWO_HOURS,
                        true)),
                ScheduleErrorCode.WORK_TYPE_NOT_SELECTABLE);
    }

    @Test
    @DisplayName("행위자 포트가 요청과 다른 조직의 행위자를 돌려주면 프로그래밍 오류로 멈추고 저장하지 않는다")
    void failsFastWhenActorBelongsToOtherOrganization() {
        LoadActorPort misbehavingPort = (accountId, organizationId) ->
                Optional.of(new ManagerActor(new MembershipId(11L), OTHER_ORGANIZATION_ID, ActorRole.OWNER));
        CreateWorkService serviceWithMisbehavingPort = new CreateWorkService(
                misbehavingPort,
                fixture.workRepository,
                fixture.workTypes,
                fixture.technicianContracts,
                fixture.scheduleLock,
                fixture.events,
                fixture.clock);

        assertThatThrownBy(() -> serviceWithMisbehavingPort.create(command("에어컨 수리", null)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BusinessException.class)
                .hasMessage("actor must belong to the requested organization");
        assertThat(fixture.workRepository.saved()).isEmpty();
    }

    @Test
    @DisplayName("업로드를 마친 현장 사진 식별자를 붙인 순서대로 작업에 남긴다")
    void keepsSitePhotos() {
        fixture.givenManager();

        service.create(photoCommand(List.of("photo-1", "photo-2")));

        assertThat(fixture.singleSaved().sitePhotos()).containsExactly("photo-1", "photo-2");
    }

    @Test
    @DisplayName("현장 사진이 6장을 넘으면 작업 유형 확인보다 먼저 입력 오류다")
    void rejectsTooManySitePhotosBeforeWorkTypeCheck() {
        fixture.givenManager();

        fixture.assertRejected(
                () -> service.create(photoCommand(Collections.nCopies(7, "photo"))),
                ScheduleErrorCode.INVALID_WORK_INPUT);
        assertThat(fixture.workTypes.checks()).isZero();
    }

    private static CreateWorkCommand photoCommand(List<String> sitePhotoIds) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                WORK_TYPE_ID.value(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sitePhotoIds,
                null,
                null,
                null,
                false);
    }

    private static CreateWorkCommand scheduled(Long technicianId, Instant start, Duration duration, boolean confirmed) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                technicianId,
                start,
                duration,
                confirmed);
    }

    private static CreateWorkCommand customerCommand(String name, String phone, String address) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                null,
                name,
                phone,
                null,
                address,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private static CreateWorkCommand command(String name, Long fee) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                name,
                2L,
                "홍길동",
                "010-1234-5678",
                "04043",
                "서울시",
                "3층",
                fee,
                PaymentMethod.ON_SITE_CARD,
                null,
                null,
                null,
                null,
                false);
    }
}
