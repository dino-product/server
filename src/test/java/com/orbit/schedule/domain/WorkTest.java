package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("작업")
class WorkTest {

    private static final MembershipId MANAGER_ID = new MembershipId(99L);

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);
    private static final CustomerInfo CUSTOMER_INFO = new CustomerInfo("홍길동", "010-1234-5678", "서울시");
    private static final PaymentInfo PAYMENT_INFO = new PaymentInfo(new Money(150000L), PaymentMethod.ON_SITE_CARD);
    private static final WorkSchedule FIRST_SCHEDULE =
            new WorkSchedule(new MembershipId(3L), Instant.parse("2026-09-22T01:00:00Z"), Duration.ofHours(2));
    private static final WorkSchedule SECOND_SCHEDULE =
            new WorkSchedule(new MembershipId(4L), Instant.parse("2026-09-23T05:00:00Z"), Duration.ofMinutes(90));
    private static final WorkSchedule RESCHEDULED_FIRST_SCHEDULE =
            new WorkSchedule(new MembershipId(3L), Instant.parse("2026-09-22T05:00:00Z"), Duration.ofHours(2));
    private static final Instant NOW = Instant.parse("2026-09-21T01:00:00Z");
    private static final CompletionReport COMPLETION_REPORT = new CompletionReport(
            List.of("before.jpg"),
            List.of("after.jpg"),
            "필터 1개",
            "필터 교체 완료",
            new Money(150000L),
            ActualPaymentMethod.CREDIT_CARD);

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("등록 상태의 작업을 생성한다")
        void registersWork() {
            Work work =
                    Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);

            assertThat(work.id()).isEmpty();
            assertThat(work.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(work.name()).isEqualTo("에어컨 수리");
            assertThat(work.registrarId()).isEqualTo(REGISTRAR_ID);
            assertThat(work.workType()).contains(WORK_TYPE_ID);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.customerInfo()).isSameAs(CUSTOMER_INFO);
            assertThat(work.paymentInfo()).isSameAs(PAYMENT_INFO);
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(work.assignmentHistory()).isEmpty();
            assertThat(work.completionReport()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("작업명이 비어 있으면 거부한다")
        void rejectsBlankName(String name) {
            assertThatThrownBy(() -> Work.register(ORGANIZATION_ID, name, REGISTRAR_ID, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("작업명은 100자까지 받고 넘으면 거부한다")
        void limitsNameLength() {
            assertThat(Work.register(ORGANIZATION_ID, "가".repeat(100), REGISTRAR_ID, null, null, null)
                            .name())
                    .hasSize(100);
            assertThatThrownBy(() -> Work.register(ORGANIZATION_ID, "가".repeat(101), REGISTRAR_ID, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must be at most 100 characters");
        }

        @Test
        @DisplayName("조직이 null이면 거부한다")
        void rejectsNullOrganizationId() {
            assertThatThrownBy(() -> Work.register(null, "에어컨 수리", REGISTRAR_ID, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("organizationId must not be null");
        }

        @Test
        @DisplayName("등록자가 null이면 거부한다")
        void rejectsNullRegistrarId() {
            assertThatThrownBy(() -> Work.register(ORGANIZATION_ID, "에어컨 수리", null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("registrarId must not be null");
        }

        @Test
        @DisplayName("선택 정보가 null이면 빈 값객체로 정규화한다")
        void normalizesNullOptionalInformation() {
            Work work = Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, null, null, null);

            assertThat(work.workType()).isEmpty();
            assertThat(work.customerInfo()).isNotNull();
            assertThat(work.customerInfo().name()).isEmpty();
            assertThat(work.customerInfo().phone()).isEmpty();
            assertThat(work.customerInfo().address()).isEmpty();
            assertThat(work.paymentInfo()).isNotNull();
            assertThat(work.paymentInfo().fee()).isEmpty();
            assertThat(work.paymentInfo().method()).isEmpty();
        }

        @Test
        @DisplayName("저장된 작업을 모든 상태와 함께 재구성한다")
        void reconstitutesWork() {
            WorkId id = new WorkId(10L);
            AssignmentHistory history = acceptedHistory(FIRST_SCHEDULE);
            List<AssignmentHistory> histories = new ArrayList<>(List.of(history));

            Work work = Work.reconstitute(
                    id,
                    ORGANIZATION_ID,
                    "에어컨 수리",
                    REGISTRAR_ID,
                    WORK_TYPE_ID,
                    FIRST_SCHEDULE,
                    CUSTOMER_INFO,
                    PAYMENT_INFO,
                    WorkStatus.COMPLETED,
                    histories,
                    COMPLETION_REPORT);
            histories.clear();

            assertThat(work.id()).contains(id);
            assertThat(work.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(work.schedule()).contains(FIRST_SCHEDULE);
            assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(work.assignmentHistory()).containsExactly(history);
            assertThat(work.completionReport()).contains(COMPLETION_REPORT);
            assertThatThrownBy(() -> work.assignmentHistory().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#consistentStoredStates")
        @DisplayName("상태와 데이터가 맞는 저장값은 재구성한다")
        void reconstitutesConsistentState(
                WorkStatus status, WorkSchedule schedule, List<AssignmentHistory> histories, CompletionReport report) {
            Work work = reconstitute(status, schedule, histories, report);

            assertThat(work.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#inconsistentStoredStates")
        @DisplayName("상태와 데이터가 어긋난 저장값은 재구성을 거부한다")
        void rejectsInconsistentState(
                WorkStatus status,
                WorkSchedule schedule,
                List<AssignmentHistory> histories,
                CompletionReport report,
                String message) {
            assertThatThrownBy(() -> reconstitute(status, schedule, histories, report))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
        }

        @Test
        @DisplayName("식별자가 null이면 재구성을 거부한다")
        void rejectsNullIdWhenReconstituting() {
            assertThatThrownBy(() -> Work.reconstitute(
                            null,
                            ORGANIZATION_ID,
                            "에어컨 수리",
                            REGISTRAR_ID,
                            null,
                            null,
                            null,
                            null,
                            WorkStatus.REGISTERED,
                            null,
                            null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("id must not be null");
        }
    }

    @Nested
    @DisplayName("기본정보 수정")
    class ChangeDetails {

        private static final WorkTypeId OTHER_WORK_TYPE_ID = new WorkTypeId(9L);
        private static final CustomerInfo OTHER_CUSTOMER_INFO = new CustomerInfo("김철수", "010-9876-5432", "부산시");
        private static final PaymentInfo OTHER_PAYMENT_INFO =
                new PaymentInfo(new Money(80_000L), PaymentMethod.BANK_TRANSFER);

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                mode = EnumSource.Mode.EXCLUDE,
                names = {"COMPLETED", "CANCELLED"})
        @DisplayName("완료·취소 전 작업의 작업명·유형·고객정보·결제정보를 바꾸고 상태·배정은 그대로 둔다")
        void changesDetailsBeforeTermination(WorkStatus status) {
            Work work = workIn(status);
            Optional<WorkSchedule> scheduleBefore = work.schedule();
            List<Tuple> historyBefore = snapshotOf(work.assignmentHistory());

            work.changeDetails("보일러 점검", OTHER_WORK_TYPE_ID, OTHER_CUSTOMER_INFO, OTHER_PAYMENT_INFO);

            assertThat(work.name()).isEqualTo("보일러 점검");
            assertThat(work.workType()).contains(OTHER_WORK_TYPE_ID);
            assertThat(work.customerInfo()).isEqualTo(OTHER_CUSTOMER_INFO);
            assertThat(work.paymentInfo()).isEqualTo(OTHER_PAYMENT_INFO);
            assertThat(work.status()).isEqualTo(status);
            assertThat(work.schedule()).isEqualTo(scheduleBefore);
            // 이력은 가변 객체라 같은 인스턴스끼리 비교하면 늘 같으므로, 바꾸기 전 값을 떠 두고 비교한다.
            assertThat(snapshotOf(work.assignmentHistory())).isEqualTo(historyBefore);
        }

        private static List<Tuple> snapshotOf(List<AssignmentHistory> histories) {
            return histories.stream()
                    .map(history -> Tuple.tuple(
                            history.schedule(),
                            history.assignedAt(),
                            history.result(),
                            history.rejectionReason(),
                            history.decidedAt()))
                    .toList();
        }

        @Test
        @DisplayName("선택 정보를 비우면 빈 값객체로 정규화한다")
        void normalizesClearedOptionalInformation() {
            Work work = registeredWork();

            work.changeDetails("보일러 점검", null, null, null);

            assertThat(work.workType()).isEmpty();
            assertThat(work.customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
            assertThat(work.paymentInfo()).isEqualTo(new PaymentInfo(null, null));
        }

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"COMPLETED", "CANCELLED"})
        @DisplayName("완료·취소된 작업은 수정할 수 없고 아무것도 바꾸지 않는다")
        void rejectsTerminatedWork(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.changeDetails("보일러 점검", null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot change details when status is " + status);
            assertUnchangedDetails(work);
        }

        @Test
        @DisplayName("완료된 작업이라도 작업명이 비었으면 입력 오류를 먼저 알린다")
        void checksInputBeforeState() {
            Work work = workIn(WorkStatus.COMPLETED);

            assertThatThrownBy(() -> work.changeDetails(" ", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("작업명이 비어 있으면 거부하고 아무것도 바꾸지 않는다")
        void rejectsBlankNameWithoutPartialChange(String name) {
            Work work = registeredWork();

            assertThatThrownBy(
                            () -> work.changeDetails(name, OTHER_WORK_TYPE_ID, OTHER_CUSTOMER_INFO, OTHER_PAYMENT_INFO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
            assertUnchangedDetails(work);
        }

        @Test
        @DisplayName("100자를 넘는 작업명으로 바꾸면 거부하고 아무것도 바꾸지 않는다")
        void rejectsTooLongNameWithoutPartialChange() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.changeDetails(
                            "가".repeat(101), OTHER_WORK_TYPE_ID, OTHER_CUSTOMER_INFO, OTHER_PAYMENT_INFO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must be at most 100 characters");
            assertUnchangedDetails(work);
        }

        private static void assertUnchangedDetails(Work work) {
            assertThat(work.name()).isEqualTo("에어컨 수리");
            assertThat(work.workType()).contains(WORK_TYPE_ID);
            assertThat(work.customerInfo()).isEqualTo(CUSTOMER_INFO);
            assertThat(work.paymentInfo()).isEqualTo(PAYMENT_INFO);
        }
    }

    @Nested
    @DisplayName("배정")
    class Assign {

        @Test
        @DisplayName("등록된 작업에 기사와 일정을 배정한다")
        void assignsRegisteredWork() {
            Work work = registeredWork();

            work.assign(FIRST_SCHEDULE, NOW, MANAGER_ID);

            assertThat(work.schedule()).contains(FIRST_SCHEDULE);
            assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
            assertThat(work.assignmentHistory()).hasSize(1);
            assertThat(work.assignmentHistory().getFirst().schedule()).isSameAs(FIRST_SCHEDULE);
            assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.PENDING);
        }

        @Test
        @DisplayName("일정이 null이면 배정을 거부한다")
        void rejectsNullSchedule() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.assign(null, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("schedule must not be null");
        }

        @Test
        @DisplayName("배정 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullAssignedAtWithoutPartialChange() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.assign(FIRST_SCHEDULE, null, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be null");
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.assignmentHistory()).isEmpty();
        }

        @Test
        @DisplayName("직전 배정 이력보다 이른 시각으로 다시 배정하면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsAssignedAtBeforeLatestAssignmentWithoutPartialChange() {
            Work work = pendingWork();
            work.reject(new Rejection(RejectionReason.OTHER, "기타 사유"), NOW.plusSeconds(60));

            assertThatThrownBy(() -> work.assign(SECOND_SCHEDULE, NOW.plusSeconds(59), MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.assignmentHistory()).singleElement().satisfies(history -> {
                assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
                assertThat(history.decidedAt()).contains(NOW.plusSeconds(60));
                assertThat(history.ending()).isEmpty();
            });
        }

        @Test
        @DisplayName("등록 외 상태에서는 배정을 거부한다")
        void rejectsAssignmentOutsideRegisteredStatus() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.assign(FIRST_SCHEDULE, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot assign when status is ACCEPTED");
        }

        @Test
        @DisplayName("불완전한 일정은 일정 값객체가 거부한다")
        void workScheduleRejectsIncompleteValues() {
            assertThatThrownBy(() -> new WorkSchedule(null, FIRST_SCHEDULE.startTime(), Duration.ofHours(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("technicianId must not be null");
            assertThatThrownBy(() -> new WorkSchedule(FIRST_SCHEDULE.technicianId(), null, Duration.ofHours(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("startTime must not be null");
            assertThatThrownBy(() ->
                            new WorkSchedule(FIRST_SCHEDULE.technicianId(), FIRST_SCHEDULE.startTime(), Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expectedDuration must be positive");
        }

        @Test
        @DisplayName("담당기사, 시작시간, 예상소요시간이 모두 없으면 배정을 거부한다")
        void rejectsAssignWithAllScheduleFieldsMissing() {
            Work work = Work.register(ORGANIZATION_ID, "필터 교체", REGISTRAR_ID, null, null, null);

            assertThatThrownBy(() -> work.assign(new WorkSchedule(null, null, null), NOW, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("배정 수락")
    class Accept {

        @Test
        @DisplayName("수락 대기 중인 배정을 수락한다")
        void acceptsPendingAssignment() {
            Work work = pendingWork();

            work.accept(NOW);

            assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.ACCEPTED);
        }

        @Test
        @DisplayName("수락 대기 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.accept(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot accept when status is REGISTERED");
        }
    }

    @Nested
    @DisplayName("배정 거절")
    class Reject {

        @Test
        @DisplayName("배정을 사유와 함께 거절하고 등록 상태로 돌린다")
        void rejectsPendingAssignment() {
            Work work = pendingWork();

            work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), NOW);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
            assertThat(history.rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("거절 사유 없이 거절할 수 없다")
        void rejectsNullReason() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reject(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("rejection must not be null");
            assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
        }

        @Test
        @DisplayName("수락 대기 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.reject(new Rejection(RejectionReason.OTHER, "기타 사유"), NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reject when status is REGISTERED");
        }
    }

    @Nested
    @DisplayName("재배정")
    class Reassign {

        @Test
        @DisplayName("기존 배정을 회수하고 새 일정을 배정한다")
        void reassignsPendingWork() {
            Work work = pendingWork();

            work.reassign(SECOND_SCHEDULE, NOW, MANAGER_ID);

            assertChangedAssignment(work, SECOND_SCHEDULE);
        }

        @Test
        @DisplayName("수락된 작업을 재배정할 때 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullTimeOnAcceptedWorkWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, null, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be null");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업을 재배정할 때 수락 시각보다 이른 시각이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsTimeBeforeAcceptanceWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, NOW.minusSeconds(1), MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업을 재배정하면 수락 이력은 두고 새 기사에게 다시 수락받는다")
        void reassignsAcceptedWorkAndRequiresReacceptance() {
            Work work = acceptedWork();

            work.reassign(SECOND_SCHEDULE, NOW, MANAGER_ID);

            assertReacceptanceRequired(work, SECOND_SCHEDULE);
        }

        @Test
        @DisplayName("같은 기사로는 재배정할 수 없다")
        void rejectsSameTechnician() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reassign(RESCHEDULED_FIRST_SCHEDULE, NOW, MANAGER_ID))
                    .isInstanceOf(SameTechnicianException.class)
                    .hasMessage("reassign requires a different technician");
        }

        @Test
        @DisplayName("새 일정이 null이면 거부한다")
        void rejectsNullSchedule() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reassign(null, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("schedule must not be null");
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#statusesThatCannotChangeAssignment")
        @DisplayName("수락 대기·수락됨 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reassign when status is " + status);
        }
    }

    @Nested
    @DisplayName("일정 변경")
    class Reschedule {

        @Test
        @DisplayName("기존 배정을 회수하고 새 일정으로 변경한다")
        void reschedulesPendingWork() {
            Work work = pendingWork();

            work.reschedule(
                    RESCHEDULED_FIRST_SCHEDULE.startTime(),
                    RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                    NOW,
                    MANAGER_ID);

            assertChangedAssignment(work, RESCHEDULED_FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꿀 때 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullTimeOnAcceptedWorkWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reschedule(
                            RESCHEDULED_FIRST_SCHEDULE.startTime(),
                            RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                            null,
                            MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be null");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꿀 때 수락 시각보다 이른 시각이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsTimeBeforeAcceptanceWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reschedule(
                            RESCHEDULED_FIRST_SCHEDULE.startTime(),
                            RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                            NOW.minusSeconds(1),
                            MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꾸면 수락 이력은 두고 다시 수락받는다")
        void reschedulesAcceptedWorkAndRequiresReacceptance() {
            Work work = acceptedWork();

            work.reschedule(
                    RESCHEDULED_FIRST_SCHEDULE.startTime(),
                    RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                    NOW,
                    MANAGER_ID);

            assertReacceptanceRequired(work, RESCHEDULED_FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("시간이 그대로면 거부한다")
        void rejectsUnchangedSchedule() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reschedule(
                            FIRST_SCHEDULE.startTime(), FIRST_SCHEDULE.expectedDuration(), NOW, MANAGER_ID))
                    .isInstanceOf(UnchangedScheduleException.class)
                    .hasMessage("reschedule requires a different time");
        }

        @Test
        @DisplayName("마이크로초보다 작은 단위만 다른 시간은 같은 시간이라 거부하고 이력을 늘리지 않는다")
        void rejectsScheduleDifferingBelowMicroseconds() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reschedule(
                            FIRST_SCHEDULE.startTime().plusNanos(500),
                            FIRST_SCHEDULE.expectedDuration().plusNanos(500),
                            NOW,
                            MANAGER_ID))
                    .isInstanceOf(UnchangedScheduleException.class)
                    .hasMessage("reschedule requires a different time");
            assertThat(work.assignmentHistory()).singleElement().satisfies(history -> {
                assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
                assertThat(history.ending()).isEmpty();
            });
        }

        @Test
        @DisplayName("담당기사는 그대로 두고 시간만 바꾼다")
        void keepsCurrentTechnician() {
            Work work = pendingWork();

            work.reschedule(
                    RESCHEDULED_FIRST_SCHEDULE.startTime(),
                    RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                    NOW,
                    MANAGER_ID);

            assertThat(work.schedule()).contains(RESCHEDULED_FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("새 시작시각이나 소요시간이 잘못되면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsInvalidTimeWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reschedule(null, Duration.ofHours(1), NOW, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("startTime must not be null");
            assertThatThrownBy(() ->
                            work.reschedule(RESCHEDULED_FIRST_SCHEDULE.startTime(), Duration.ZERO, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expectedDuration must be positive");
            assertUnchangedAcceptedWork(work);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#statusesThatCannotChangeAssignment")
        @DisplayName("수락 대기·수락됨 상태가 아니면 입력과 관계없이 상태 오류로 거부한다")
        void rejectsOutsidePendingStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.reschedule(null, null, NOW, MANAGER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reschedule when status is " + status);
        }
    }

    @Nested
    @DisplayName("배정 해제")
    class Unassign {

        @Test
        @DisplayName("수락 대기 배정을 회수하고 등록 상태로 돌린다")
        void unassignsPendingWork() {
            Work work = pendingWork();

            work.unassign(NOW, MANAGER_ID);

            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.WITHDRAWN);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("수락 결과는 그대로 두고 해제를 종료로 남긴 채 등록 상태로 돌린다")
        void unassignsAcceptedWorkKeepingResult() {
            Work work = acceptedWork();

            work.unassign(NOW, MANAGER_ID);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW, MANAGER_ID, AssignmentEndReason.UNASSIGNED));
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("수락 시각보다 앞선 시각으로는 해제하지 않고 작업을 전혀 바꾸지 않는다")
        void rejectsUnassigningBeforeAcceptance() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.unassign(NOW.minusSeconds(1), MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endedAt must not be before the assignment was assigned or decided");
            assertUnchangedAcceptedWork(work);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#statusesThatCannotBeUnassigned")
        @DisplayName("해제할 수 없는 상태에서는 거부한다")
        void rejectsDisallowedStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.unassign(NOW, MANAGER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot unassign when status is " + status);
        }
    }

    @Nested
    @DisplayName("작업 시작")
    class Start {

        @Test
        @DisplayName("수락된 작업을 시작한다")
        void startsAcceptedWork() {
            Work work = acceptedWork();

            work.start();

            assertThat(work.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("수락 상태가 아니면 거부한다")
        void rejectsOutsideAcceptedStatus() {
            Work work = pendingWork();

            assertThatThrownBy(work::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot start when status is PENDING_ACCEPTANCE");
        }
    }

    @Nested
    @DisplayName("완료보고 제출")
    class SubmitCompletionReport {

        @Test
        @DisplayName("작업 중인 작업에 완료보고를 제출한다")
        void submitsCompletionReport() {
            Work work = inProgressWork();

            work.submitCompletionReport(COMPLETION_REPORT);

            assertThat(work.completionReport()).contains(COMPLETION_REPORT);
            assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        }

        @Test
        @DisplayName("완료보고가 null이면 거부한다")
        void rejectsNullReport() {
            Work work = inProgressWork();

            assertThatThrownBy(() -> work.submitCompletionReport(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("completionReport must not be null");
        }

        @Test
        @DisplayName("작업 중 상태가 아니면 거부한다")
        void rejectsOutsideInProgressStatus() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.submitCompletionReport(COMPLETION_REPORT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot submit completion report when status is ACCEPTED");
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#cancellableStatuses")
        @DisplayName("허용된 상태의 작업을 취소한다")
        void cancelsAllowedStatus(WorkStatus status) {
            Work work = workIn(status);

            work.cancel(NOW, MANAGER_ID);

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        }

        @Test
        @DisplayName("응답 대기 중인 배정을 취소 시각으로 회수한다")
        void closesPendingAssignment() {
            Work work = pendingWork();
            Instant cancelledAt = NOW.plusSeconds(60);

            work.cancel(cancelledAt, MANAGER_ID);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.WITHDRAWN);
            assertThat(history.decidedAt()).contains(cancelledAt);
        }

        @Test
        @DisplayName("수락된 배정은 결과를 그대로 두고 취소를 종료로 남긴다")
        void keepsAcceptedResult() {
            Work work = acceptedWork();

            work.cancel(NOW.plusSeconds(60), MANAGER_ID);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(NOW);
            assertThat(history.ending())
                    .contains(new AssignmentEnding(NOW.plusSeconds(60), MANAGER_ID, AssignmentEndReason.CANCELLED));
        }

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"ACCEPTED", "IN_PROGRESS"})
        @DisplayName("수락 시각보다 앞선 시각으로는 취소하지 않고 작업을 전혀 바꾸지 않는다")
        void rejectsCancellingBeforeAcceptance(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.cancel(NOW.minusSeconds(1), MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endedAt must not be before the assignment was assigned or decided");
            assertThat(work.status()).isEqualTo(status);
            assertThat(work.assignmentHistory().getLast().ending()).isEmpty();
        }

        @Test
        @DisplayName("대기함 작업이라도 취소 시각이 없으면 상태와 관계없이 입력 오류다")
        void requiresCancellationTimeEvenInBacklog() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.cancel(null, MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endedAt must not be null");
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"COMPLETED", "CANCELLED"})
        @DisplayName("종료된 작업은 취소할 수 없다")
        void rejectsTerminalStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.cancel(NOW, MANAGER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot transition from %s to CANCELLED", status);
        }
    }

    @Nested
    @DisplayName("처리자·종료 기록")
    class ActorRecords {

        private static final MembershipId OTHER_MANAGER_ID = new MembershipId(98L);
        private static final Instant LATER = NOW.plusSeconds(60);

        @Test
        @DisplayName("배정한 관리자를 새 배정 이력에 남긴다")
        void recordsWhoAssigned() {
            Work work = pendingWork();

            assertThat(work.assignmentHistory().getLast().assignedBy()).isEqualTo(MANAGER_ID);
            assertThat(work.assignmentHistory().getLast().ending()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"PENDING_ACCEPTANCE", "ACCEPTED"})
        @DisplayName("재배정하면 이전 배정의 종료(시각·처리자·재배정)와 새 배정의 배정자를 남긴다")
        void recordsReassignment(WorkStatus status) {
            Work work = workIn(status);

            work.reassign(SECOND_SCHEDULE, LATER, OTHER_MANAGER_ID);

            AssignmentHistory previous = work.assignmentHistory().getFirst();
            assertThat(previous.ending())
                    .contains(new AssignmentEnding(LATER, OTHER_MANAGER_ID, AssignmentEndReason.REASSIGNED));
            assertThat(previous.result())
                    .isEqualTo(status == WorkStatus.ACCEPTED ? AssignmentResult.ACCEPTED : AssignmentResult.WITHDRAWN);
            assertThat(work.assignmentHistory().getLast().assignedBy()).isEqualTo(OTHER_MANAGER_ID);
        }

        @Test
        @DisplayName("일정 변경은 이전 배정을 일정 변경으로 끝낸다")
        void recordsReschedule() {
            Work work = acceptedWork();

            work.reschedule(
                    RESCHEDULED_FIRST_SCHEDULE.startTime(),
                    RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                    LATER,
                    OTHER_MANAGER_ID);

            assertThat(work.assignmentHistory().getFirst().ending())
                    .contains(new AssignmentEnding(LATER, OTHER_MANAGER_ID, AssignmentEndReason.RESCHEDULED));
        }

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"PENDING_ACCEPTANCE", "ACCEPTED"})
        @DisplayName("배정 해제는 수락된 배정이라도 해제 시각·처리자를 종료로 남긴다")
        void recordsUnassignment(WorkStatus status) {
            Work work = workIn(status);

            work.unassign(LATER, OTHER_MANAGER_ID);

            assertThat(work.assignmentHistory().getLast().ending())
                    .contains(new AssignmentEnding(LATER, OTHER_MANAGER_ID, AssignmentEndReason.UNASSIGNED));
        }

        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"PENDING_ACCEPTANCE", "ACCEPTED", "IN_PROGRESS"})
        @DisplayName("배정이 있는 작업을 취소하면 그 배정을 취소로 끝낸다")
        void recordsCancellationOfCurrentAssignment(WorkStatus status) {
            Work work = workIn(status);

            work.cancel(LATER, OTHER_MANAGER_ID);

            assertThat(work.assignmentHistory().getLast().ending())
                    .contains(new AssignmentEnding(LATER, OTHER_MANAGER_ID, AssignmentEndReason.CANCELLED));
        }

        @Test
        @DisplayName("대기함 작업을 취소하면 이미 끝난 이전 배정의 기록은 바꾸지 않는다")
        void keepsEndedHistoryWhenCancellingBacklogWork() {
            Work work = acceptedWork();
            work.unassign(LATER, OTHER_MANAGER_ID);

            work.cancel(LATER.plusSeconds(60), MANAGER_ID);

            assertThat(work.assignmentHistory().getLast().ending())
                    .contains(new AssignmentEnding(LATER, OTHER_MANAGER_ID, AssignmentEndReason.UNASSIGNED));
        }

        @Test
        @DisplayName("수락된 배정이 끝난 시각보다 앞서 다시 배정할 수 없다")
        void keepsChronologyAfterEndingAcceptedAssignment() {
            Work work = acceptedWork();
            work.unassign(LATER, OTHER_MANAGER_ID);

            assertThatThrownBy(() -> work.assign(SECOND_SCHEDULE, LATER.minusSeconds(1), MANAGER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
        }

        @Test
        @DisplayName("처리자가 없으면 배정·재배정·일정 변경·해제·취소를 거부하고 작업을 바꾸지 않는다")
        void requiresActor() {
            Work registered = registeredWork();
            assertThatThrownBy(() -> registered.assign(FIRST_SCHEDULE, NOW, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedBy must not be null");
            assertThat(registered.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(registered.schedule()).isEmpty();
            assertThat(registered.assignmentHistory()).isEmpty();
            assertPendingWorkRequiresActor(
                    "assignedBy must not be null", work -> work.reassign(SECOND_SCHEDULE, LATER, null));
            assertPendingWorkRequiresActor(
                    "assignedBy must not be null",
                    work -> work.reschedule(
                            RESCHEDULED_FIRST_SCHEDULE.startTime(),
                            RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                            LATER,
                            null));
            assertPendingWorkRequiresActor("endedBy must not be null", work -> work.unassign(LATER, null));
            assertPendingWorkRequiresActor("endedBy must not be null", work -> work.cancel(LATER, null));
        }

        private static void assertPendingWorkRequiresActor(String message, Consumer<Work> call) {
            Work pending = pendingWork();

            assertThatThrownBy(() -> call.accept(pending))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
            assertThat(pending.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
            assertThat(pending.schedule()).contains(FIRST_SCHEDULE);
            assertThat(pending.assignmentHistory()).singleElement().satisfies(history -> {
                assertThat(history.result()).isEqualTo(AssignmentResult.PENDING);
                assertThat(history.ending()).isEmpty();
            });
        }

        @Test
        @DisplayName("수락된 작업도 처리자가 없으면 재배정·일정 변경을 거부하고 작업을 전혀 바꾸지 않는다")
        void requiresActorForAcceptedWork() {
            Work accepted = acceptedWork();

            assertThatThrownBy(() -> accepted.reassign(SECOND_SCHEDULE, LATER, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedBy must not be null");
            assertThatThrownBy(() -> accepted.reschedule(
                            RESCHEDULED_FIRST_SCHEDULE.startTime(),
                            RESCHEDULED_FIRST_SCHEDULE.expectedDuration(),
                            LATER,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedBy must not be null");
            assertUnchangedAcceptedWork(accepted);
            assertThat(accepted.assignmentHistory().getLast().ending()).isEmpty();
        }
    }

    private static Work registeredWork() {
        return Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }

    private static Work pendingWork() {
        Work work = registeredWork();
        work.assign(FIRST_SCHEDULE, NOW, MANAGER_ID);
        return work;
    }

    private static Work acceptedWork() {
        Work work = pendingWork();
        work.accept(NOW);
        return work;
    }

    private static Work inProgressWork() {
        Work work = acceptedWork();
        work.start();
        return work;
    }

    private static Work completedWork() {
        Work work = inProgressWork();
        work.submitCompletionReport(COMPLETION_REPORT);
        return work;
    }

    private static Work cancelledWork() {
        Work work = registeredWork();
        work.cancel(NOW, MANAGER_ID);
        return work;
    }

    /** 실제 전이 흐름으로 주어진 상태의 작업을 만든다. */
    private static Work workIn(WorkStatus status) {
        return switch (status) {
            case REGISTERED -> registeredWork();
            case PENDING_ACCEPTANCE -> pendingWork();
            case ACCEPTED -> acceptedWork();
            case IN_PROGRESS -> inProgressWork();
            case COMPLETED -> completedWork();
            case CANCELLED -> cancelledWork();
        };
    }

    private static Work reconstitute(
            WorkStatus status, WorkSchedule schedule, List<AssignmentHistory> histories, CompletionReport report) {
        return Work.reconstitute(
                new WorkId(10L),
                ORGANIZATION_ID,
                "에어컨 수리",
                REGISTRAR_ID,
                WORK_TYPE_ID,
                schedule,
                CUSTOMER_INFO,
                PAYMENT_INFO,
                status,
                histories,
                report);
    }

    private static AssignmentHistory pendingHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(schedule, NOW, MANAGER_ID, AssignmentResult.PENDING, null, null, null);
    }

    private static AssignmentHistory acceptedHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(schedule, NOW, MANAGER_ID, AssignmentResult.ACCEPTED, null, NOW, null);
    }

    private static AssignmentHistory rejectedHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(
                schedule,
                NOW,
                MANAGER_ID,
                AssignmentResult.REJECTED,
                new Rejection(RejectionReason.OTHER, "기타 사유"),
                NOW,
                null);
    }

    private static AssignmentHistory closedHistory(WorkSchedule schedule) {
        return closedHistory(schedule, AssignmentEndReason.REASSIGNED);
    }

    /** 응답 전에 관리자 조치로 끝난 배정. */
    private static AssignmentHistory closedHistory(WorkSchedule schedule, AssignmentEndReason reason) {
        return AssignmentHistory.restore(
                schedule,
                NOW,
                MANAGER_ID,
                AssignmentResult.WITHDRAWN,
                null,
                NOW,
                new AssignmentEnding(NOW, MANAGER_ID, reason));
    }

    /** 수락된 뒤 관리자 조치로 끝난 배정. */
    private static AssignmentHistory endedAcceptedHistory(WorkSchedule schedule, AssignmentEndReason reason) {
        return endedAcceptedHistory(schedule, reason, NOW);
    }

    private static AssignmentHistory endedAcceptedHistory(
            WorkSchedule schedule, AssignmentEndReason reason, Instant endedAt) {
        return AssignmentHistory.restore(
                schedule,
                NOW,
                MANAGER_ID,
                AssignmentResult.ACCEPTED,
                null,
                NOW,
                new AssignmentEnding(endedAt, MANAGER_ID, reason));
    }

    private static AssignmentHistory pendingHistory(WorkSchedule schedule, Instant assignedAt, MembershipId by) {
        return AssignmentHistory.restore(schedule, assignedAt, by, AssignmentResult.PENDING, null, null, null);
    }

    private static Stream<Arguments> consistentStoredStates() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED, null, List.of(), null),
                Arguments.of(WorkStatus.REGISTERED, null, List.of(rejectedHistory(FIRST_SCHEDULE)), null),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        null,
                        List.of(endedAcceptedHistory(FIRST_SCHEDULE, AssignmentEndReason.UNASSIGNED)),
                        null),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        null,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.UNASSIGNED)),
                        null),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(closedHistory(FIRST_SCHEDULE), pendingHistory(SECOND_SCHEDULE)),
                        null),
                Arguments.of(WorkStatus.ACCEPTED, FIRST_SCHEDULE, List.of(acceptedHistory(FIRST_SCHEDULE)), null),
                Arguments.of(WorkStatus.IN_PROGRESS, FIRST_SCHEDULE, List.of(acceptedHistory(FIRST_SCHEDULE)), null),
                Arguments.of(
                        WorkStatus.COMPLETED,
                        FIRST_SCHEDULE,
                        List.of(acceptedHistory(FIRST_SCHEDULE)),
                        COMPLETION_REPORT),
                Arguments.of(WorkStatus.CANCELLED, null, List.of(), null),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        FIRST_SCHEDULE,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.CANCELLED)),
                        null),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        FIRST_SCHEDULE,
                        List.of(endedAcceptedHistory(FIRST_SCHEDULE, AssignmentEndReason.CANCELLED)),
                        null),
                Arguments.of(
                        WorkStatus.ACCEPTED,
                        SECOND_SCHEDULE,
                        List.of(
                                endedAcceptedHistory(FIRST_SCHEDULE, AssignmentEndReason.REASSIGNED),
                                acceptedHistory(SECOND_SCHEDULE)),
                        null));
    }

    private static Stream<Arguments> inconsistentStoredStates() {
        return Stream.of(
                Arguments.of(
                        WorkStatus.CANCELLED,
                        FIRST_SCHEDULE,
                        List.of(),
                        null,
                        "CANCELLED work from the backlog must not have a schedule"),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        FIRST_SCHEDULE,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.UNASSIGNED)),
                        null,
                        "CANCELLED work from the backlog must not have a schedule"),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        SECOND_SCHEDULE,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.CANCELLED)),
                        null,
                        "CANCELLED work must keep the schedule of the cancelled assignment"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.RESCHEDULED),
                                pendingHistory(SECOND_SCHEDULE)),
                        null,
                        "assignment ended by RESCHEDULED must be followed by a matching technician"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        RESCHEDULED_FIRST_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.REASSIGNED),
                                pendingHistory(RESCHEDULED_FIRST_SCHEDULE)),
                        null,
                        "assignment ended by REASSIGNED must be followed by a matching technician"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        FIRST_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.RESCHEDULED),
                                pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "assignment ended by RESCHEDULED must be followed by a different schedule"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.REASSIGNED),
                                pendingHistory(SECOND_SCHEDULE, NOW.plusSeconds(1), MANAGER_ID)),
                        null,
                        "assignment ended by REASSIGNED must be followed by the assignment it made"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.REASSIGNED),
                                pendingHistory(SECOND_SCHEDULE, NOW, new MembershipId(98L))),
                        null,
                        "assignment ended by REASSIGNED must be followed by the assignment it made"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                endedAcceptedHistory(
                                        FIRST_SCHEDULE, AssignmentEndReason.UNASSIGNED, NOW.plusSeconds(60)),
                                pendingHistory(SECOND_SCHEDULE, NOW.plusSeconds(30), MANAGER_ID)),
                        null,
                        "assignment histories must be in chronological order"),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        null,
                        List.of(acceptedHistory(FIRST_SCHEDULE)),
                        null,
                        "REGISTERED work must not have a current assignment"),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        null,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.CANCELLED)),
                        null,
                        "REGISTERED work must not have an assignment ended by CANCELLED"),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        null,
                        List.of(closedHistory(FIRST_SCHEDULE, AssignmentEndReason.RESCHEDULED)),
                        null,
                        "CANCELLED work must not have an assignment ended by RESCHEDULED"),
                Arguments.of(
                        WorkStatus.ACCEPTED,
                        SECOND_SCHEDULE,
                        List.of(acceptedHistory(FIRST_SCHEDULE), acceptedHistory(SECOND_SCHEDULE)),
                        null,
                        "Only the latest assignment history can be current"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                closedHistory(FIRST_SCHEDULE, AssignmentEndReason.CANCELLED),
                                pendingHistory(SECOND_SCHEDULE)),
                        null,
                        "Only the latest assignment history can end by cancellation"),
                Arguments.of(
                        WorkStatus.ACCEPTED,
                        FIRST_SCHEDULE,
                        List.of(endedAcceptedHistory(FIRST_SCHEDULE, AssignmentEndReason.UNASSIGNED)),
                        null,
                        "ACCEPTED work requires the latest assignment to be ACCEPTED for its schedule"),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        FIRST_SCHEDULE,
                        List.of(),
                        null,
                        "REGISTERED work must not have a schedule"),
                Arguments.of(
                        WorkStatus.REGISTERED,
                        null,
                        List.of(pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "REGISTERED work must not have a PENDING assignment"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        FIRST_SCHEDULE,
                        List.of(),
                        null,
                        "PENDING_ACCEPTANCE work requires the latest assignment to be PENDING for its schedule"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        null,
                        List.of(pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "PENDING_ACCEPTANCE work must have a schedule"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "PENDING_ACCEPTANCE work requires the latest assignment to be PENDING for its schedule"),
                Arguments.of(
                        WorkStatus.ACCEPTED,
                        FIRST_SCHEDULE,
                        List.of(pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "ACCEPTED work requires the latest assignment to be ACCEPTED for its schedule"),
                Arguments.of(
                        WorkStatus.IN_PROGRESS,
                        null,
                        List.of(acceptedHistory(FIRST_SCHEDULE)),
                        null,
                        "IN_PROGRESS work must have a schedule"),
                Arguments.of(
                        WorkStatus.COMPLETED,
                        FIRST_SCHEDULE,
                        List.of(acceptedHistory(FIRST_SCHEDULE)),
                        null,
                        "COMPLETED work must have a completion report"),
                Arguments.of(
                        WorkStatus.ACCEPTED,
                        FIRST_SCHEDULE,
                        List.of(acceptedHistory(FIRST_SCHEDULE)),
                        COMPLETION_REPORT,
                        "ACCEPTED work must not have a completion report"),
                Arguments.of(
                        WorkStatus.CANCELLED,
                        FIRST_SCHEDULE,
                        List.of(pendingHistory(FIRST_SCHEDULE)),
                        null,
                        "CANCELLED work must not have a PENDING assignment"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(pendingHistory(FIRST_SCHEDULE), pendingHistory(SECOND_SCHEDULE)),
                        null,
                        "Only the latest assignment history can be PENDING"),
                Arguments.of(
                        WorkStatus.PENDING_ACCEPTANCE,
                        SECOND_SCHEDULE,
                        List.of(
                                AssignmentHistory.restore(
                                        FIRST_SCHEDULE,
                                        NOW,
                                        MANAGER_ID,
                                        AssignmentResult.REJECTED,
                                        new Rejection(RejectionReason.OTHER, "기타 사유"),
                                        NOW.plusSeconds(100),
                                        null),
                                AssignmentHistory.restore(
                                        SECOND_SCHEDULE,
                                        NOW.plusSeconds(50),
                                        MANAGER_ID,
                                        AssignmentResult.PENDING,
                                        null,
                                        null,
                                        null)),
                        null,
                        "assignment histories must be in chronological order"));
    }

    private static void assertChangedAssignment(Work work, WorkSchedule newSchedule) {
        assertThat(work.schedule()).contains(newSchedule);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.WITHDRAWN);
        assertThat(work.assignmentHistory().getLast().schedule()).isEqualTo(newSchedule);
        assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
    }

    private static void assertUnchangedAcceptedWork(Work work) {
        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        assertThat(work.assignmentHistory()).hasSize(1);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(work.assignmentHistory().getFirst().ending()).isEmpty();
    }

    private static void assertReacceptanceRequired(Work work, WorkSchedule newSchedule) {
        assertThat(work.schedule()).contains(newSchedule);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(work.assignmentHistory().getLast().schedule()).isEqualTo(newSchedule);
        assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
    }

    private static Stream<Arguments> cancellableStatuses() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.ACCEPTED),
                Arguments.of(WorkStatus.IN_PROGRESS));
    }

    private static Stream<Arguments> statusesThatCannotChangeAssignment() {
        return statusesThatCannotBeUnassigned();
    }

    private static Stream<Arguments> statusesThatCannotBeUnassigned() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.CANCELLED));
    }
}
