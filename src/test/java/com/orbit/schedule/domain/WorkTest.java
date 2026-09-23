package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("작업")
class WorkTest {

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
            Work work = Work.register("에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);

            assertThat(work.id()).isEmpty();
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
            assertThatThrownBy(() -> Work.register(name, REGISTRAR_ID, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name must not be blank");
        }

        @Test
        @DisplayName("등록자가 null이면 거부한다")
        void rejectsNullRegistrarId() {
            assertThatThrownBy(() -> Work.register("에어컨 수리", null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("registrarId must not be null");
        }

        @Test
        @DisplayName("선택 정보가 null이면 빈 값객체로 정규화한다")
        void normalizesNullOptionalInformation() {
            Work work = Work.register("에어컨 수리", REGISTRAR_ID, null, null, null);

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
                            null, "에어컨 수리", REGISTRAR_ID, null, null, null, null, WorkStatus.REGISTERED, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("id must not be null");
        }
    }

    @Nested
    @DisplayName("배정")
    class Assign {

        @Test
        @DisplayName("등록된 작업에 기사와 일정을 배정한다")
        void assignsRegisteredWork() {
            Work work = registeredWork();

            work.assign(FIRST_SCHEDULE, NOW);

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

            assertThatThrownBy(() -> work.assign(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("schedule must not be null");
        }

        @Test
        @DisplayName("배정 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullAssignedAtWithoutPartialChange() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.assign(FIRST_SCHEDULE, null))
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
            work.reject(RejectionReason.OTHER, NOW.plusSeconds(60));

            assertThatThrownBy(() -> work.assign(SECOND_SCHEDULE, NOW.plusSeconds(59)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.assignmentHistory()).hasSize(1);
        }

        @Test
        @DisplayName("등록 외 상태에서는 배정을 거부한다")
        void rejectsAssignmentOutsideRegisteredStatus() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.assign(FIRST_SCHEDULE, NOW))
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
            Work work = Work.register("필터 교체", REGISTRAR_ID, null, null, null);

            assertThatThrownBy(() -> work.assign(new WorkSchedule(null, null, null), NOW))
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

            work.reject(RejectionReason.SCHEDULE_CONFLICT, NOW);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.REJECTED);
            assertThat(history.rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("거절 사유가 null이면 거부한다")
        void rejectsNullReason() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reject(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("rejectionReason must not be null");
            assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
        }

        @Test
        @DisplayName("수락 대기 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.reject(RejectionReason.OTHER, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reject when status is REGISTERED");
        }
    }

    @Nested
    @DisplayName("재배정")
    class Reassign {

        @Test
        @DisplayName("기존 배정을 마감하고 새 일정을 배정한다")
        void reassignsPendingWork() {
            Work work = pendingWork();

            work.reassign(SECOND_SCHEDULE, NOW);

            assertChangedAssignment(work, SECOND_SCHEDULE);
        }

        @Test
        @DisplayName("수락된 작업을 재배정할 때 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullTimeOnAcceptedWorkWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be null");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업을 재배정할 때 수락 시각보다 이른 시각이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsTimeBeforeAcceptanceWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, NOW.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업을 재배정하면 수락 이력은 두고 새 기사에게 다시 수락받는다")
        void reassignsAcceptedWorkAndRequiresReacceptance() {
            Work work = acceptedWork();

            work.reassign(SECOND_SCHEDULE, NOW);

            assertReacceptanceRequired(work, SECOND_SCHEDULE);
        }

        @Test
        @DisplayName("같은 기사로는 재배정할 수 없다")
        void rejectsSameTechnician() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reassign(RESCHEDULED_FIRST_SCHEDULE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reassign requires a different technician");
        }

        @Test
        @DisplayName("새 일정이 null이면 거부한다")
        void rejectsNullSchedule() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reassign(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("schedule must not be null");
        }

        @Test
        @DisplayName("수락 대기·수락됨 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.reassign(SECOND_SCHEDULE, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reassign when status is REGISTERED");
        }
    }

    @Nested
    @DisplayName("일정 변경")
    class Reschedule {

        @Test
        @DisplayName("기존 배정을 마감하고 새 일정으로 변경한다")
        void reschedulesPendingWork() {
            Work work = pendingWork();

            work.reschedule(RESCHEDULED_FIRST_SCHEDULE, NOW);

            assertChangedAssignment(work, RESCHEDULED_FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꿀 때 시각이 null이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsNullTimeOnAcceptedWorkWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reschedule(RESCHEDULED_FIRST_SCHEDULE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be null");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꿀 때 수락 시각보다 이른 시각이면 거부하고 작업을 전혀 바꾸지 않는다")
        void rejectsTimeBeforeAcceptanceWithoutPartialChange() {
            Work work = acceptedWork();

            assertThatThrownBy(() -> work.reschedule(RESCHEDULED_FIRST_SCHEDULE, NOW.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("assignedAt must not be before the latest assignment");
            assertUnchangedAcceptedWork(work);
        }

        @Test
        @DisplayName("수락된 작업의 일정을 바꾸면 수락 이력은 두고 다시 수락받는다")
        void reschedulesAcceptedWorkAndRequiresReacceptance() {
            Work work = acceptedWork();

            work.reschedule(RESCHEDULED_FIRST_SCHEDULE, NOW);

            assertReacceptanceRequired(work, RESCHEDULED_FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("담당기사가 바뀌면 일정 변경이 아니라 재배정이라 거부한다")
        void rejectsDifferentTechnician() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reschedule(SECOND_SCHEDULE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reschedule requires the same technician");
        }

        @Test
        @DisplayName("시간이 그대로면 거부한다")
        void rejectsUnchangedSchedule() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reschedule(FIRST_SCHEDULE, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reschedule requires a different time");
        }

        @Test
        @DisplayName("새 일정이 null이면 거부한다")
        void rejectsNullSchedule() {
            Work work = pendingWork();

            assertThatThrownBy(() -> work.reschedule(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("schedule must not be null");
        }

        @Test
        @DisplayName("수락 대기·수락됨 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.reschedule(RESCHEDULED_FIRST_SCHEDULE, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot reschedule when status is REGISTERED");
        }
    }

    @Nested
    @DisplayName("배정 해제")
    class Unassign {

        @Test
        @DisplayName("수락 대기 배정을 마감하고 등록 상태로 돌린다")
        void unassignsPendingWork() {
            Work work = pendingWork();

            work.unassign(NOW);

            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.REASSIGNED);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("수락된 배정 이력을 보존하고 등록 상태로 돌린다")
        void unassignsAcceptedWorkWithoutChangingHistory() {
            Work work = acceptedWork();

            work.unassign(NOW);

            assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(work.schedule()).isEmpty();
            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.domain.WorkTest#statusesThatCannotBeUnassigned")
        @DisplayName("해제할 수 없는 상태에서는 거부한다")
        void rejectsDisallowedStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.unassign(NOW))
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

            work.cancel(NOW);

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        }

        @Test
        @DisplayName("응답 대기 중인 배정을 취소 시각으로 마감한다")
        void closesPendingAssignment() {
            Work work = pendingWork();
            Instant cancelledAt = NOW.plusSeconds(60);

            work.cancel(cancelledAt);

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.REASSIGNED);
            assertThat(history.decidedAt()).contains(cancelledAt);
        }

        @Test
        @DisplayName("수락된 배정 이력은 그대로 둔다")
        void keepsAcceptedAssignment() {
            Work work = acceptedWork();

            work.cancel(NOW.plusSeconds(60));

            AssignmentHistory history = work.assignmentHistory().getLast();
            assertThat(history.result()).isEqualTo(AssignmentResult.ACCEPTED);
            assertThat(history.decidedAt()).contains(NOW);
        }

        @ParameterizedTest
        @ValueSource(strings = {"COMPLETED", "CANCELLED"})
        @DisplayName("종료된 작업은 취소할 수 없다")
        void rejectsTerminalStatus(WorkStatus status) {
            Work work = workIn(status);

            assertThatThrownBy(() -> work.cancel(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot transition from %s to CANCELLED", status);
        }
    }

    private static Work registeredWork() {
        return Work.register("에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }

    private static Work pendingWork() {
        Work work = registeredWork();
        work.assign(FIRST_SCHEDULE, NOW);
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
        work.cancel(NOW);
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
        return AssignmentHistory.restore(schedule, NOW, AssignmentResult.PENDING, null, null);
    }

    private static AssignmentHistory acceptedHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(schedule, NOW, AssignmentResult.ACCEPTED, null, NOW);
    }

    private static AssignmentHistory rejectedHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(schedule, NOW, AssignmentResult.REJECTED, RejectionReason.OTHER, NOW);
    }

    private static AssignmentHistory closedHistory(WorkSchedule schedule) {
        return AssignmentHistory.restore(schedule, NOW, AssignmentResult.REASSIGNED, null, NOW);
    }

    private static Stream<Arguments> consistentStoredStates() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED, null, List.of(), null),
                Arguments.of(WorkStatus.REGISTERED, null, List.of(rejectedHistory(FIRST_SCHEDULE)), null),
                Arguments.of(WorkStatus.REGISTERED, null, List.of(acceptedHistory(FIRST_SCHEDULE)), null),
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
                Arguments.of(WorkStatus.CANCELLED, FIRST_SCHEDULE, List.of(closedHistory(FIRST_SCHEDULE)), null));
    }

    private static Stream<Arguments> inconsistentStoredStates() {
        return Stream.of(
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
                        "Only the latest assignment history can be PENDING"));
    }

    private static void assertChangedAssignment(Work work, WorkSchedule newSchedule) {
        assertThat(work.schedule()).contains(newSchedule);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.REASSIGNED);
        assertThat(work.assignmentHistory().getLast().schedule()).isSameAs(newSchedule);
        assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
    }

    private static void assertUnchangedAcceptedWork(Work work) {
        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        assertThat(work.assignmentHistory()).hasSize(1);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
    }

    private static void assertReacceptanceRequired(Work work, WorkSchedule newSchedule) {
        assertThat(work.schedule()).contains(newSchedule);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
        assertThat(work.assignmentHistory().getLast().schedule()).isSameAs(newSchedule);
        assertThat(work.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.PENDING);
    }

    private static Stream<Arguments> cancellableStatuses() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.PENDING_ACCEPTANCE),
                Arguments.of(WorkStatus.ACCEPTED),
                Arguments.of(WorkStatus.IN_PROGRESS));
    }

    private static Stream<Arguments> statusesThatCannotBeUnassigned() {
        return Stream.of(
                Arguments.of(WorkStatus.REGISTERED),
                Arguments.of(WorkStatus.IN_PROGRESS),
                Arguments.of(WorkStatus.COMPLETED),
                Arguments.of(WorkStatus.CANCELLED));
    }
}
