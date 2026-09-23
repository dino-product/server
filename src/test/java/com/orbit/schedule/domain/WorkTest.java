package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
    private static final PaymentInfo PAYMENT_INFO =
            new PaymentInfo(new BigDecimal("150000"), PaymentMethod.ON_SITE_CARD);
    private static final WorkSchedule FIRST_SCHEDULE =
            new WorkSchedule(new MembershipId(3L), Instant.parse("2026-09-22T01:00:00Z"), Duration.ofHours(2));
    private static final WorkSchedule SECOND_SCHEDULE =
            new WorkSchedule(new MembershipId(4L), Instant.parse("2026-09-23T05:00:00Z"), Duration.ofMinutes(90));
    private static final Instant NOW = Instant.parse("2026-09-21T01:00:00Z");
    private static final CompletionReport COMPLETION_REPORT = new CompletionReport(
            List.of("before.jpg"),
            List.of("after.jpg"),
            "필터 1개",
            "필터 교체 완료",
            new BigDecimal("150000"),
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
            AssignmentHistory history = new AssignmentHistory(FIRST_SCHEDULE, NOW);
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
        @DisplayName("등록 외 상태에서는 배정을 거부한다")
        void rejectsAssignmentOutsideRegisteredStatus() {
            Work work = registeredWork();
            work.forceStatus(WorkStatus.ACCEPTED);

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

        @Test
        @DisplayName("배정 이력이 없으면 거부한다")
        void rejectsMissingAssignmentHistory() {
            Work work = reconstitutedWithoutHistory(WorkStatus.PENDING_ACCEPTANCE);

            assertThatThrownBy(() -> work.accept(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No assignment history");
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

            assertChangedAssignment(work);
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
        @DisplayName("수락 대기 상태가 아니면 거부한다")
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

            work.reschedule(SECOND_SCHEDULE, NOW);

            assertChangedAssignment(work);
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
        @DisplayName("수락 대기 상태가 아니면 거부한다")
        void rejectsOutsidePendingStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.reschedule(SECOND_SCHEDULE, NOW))
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
            Work work = registeredWork();
            work.forceStatus(status);

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
            Work work = registeredWork();
            work.forceStatus(status);

            work.cancel();

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"COMPLETED", "CANCELLED"})
        @DisplayName("종료된 작업은 취소할 수 없다")
        void rejectsTerminalStatus(WorkStatus status) {
            Work work = registeredWork();
            work.forceStatus(status);

            assertThatThrownBy(work::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot transition from %s to CANCELLED", status);
        }
    }

    @Nested
    @DisplayName("관리자 상태 강제 변경")
    class ForceStatus {

        @Test
        @DisplayName("화이트리스트에 없는 상태 전이도 강제로 적용한다")
        void bypassesTransitionWhitelist() {
            Work work = registeredWork();
            work.forceStatus(WorkStatus.COMPLETED);

            work.forceStatus(WorkStatus.REGISTERED);

            assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("REGISTERED로 강제 변경하면 배정된 일정을 정리한다")
        void clearsScheduleWhenForcedToRegistered() {
            Work work = acceptedWork();

            work.forceStatus(WorkStatus.REGISTERED);

            assertThat(work.schedule()).isEmpty();
        }

        @Test
        @DisplayName("REGISTERED가 아닌 상태로 강제 변경하면 배정된 일정을 그대로 둔다")
        void keepsScheduleWhenForcedToNonRegisteredStatus() {
            Work work = acceptedWork();

            work.forceStatus(WorkStatus.COMPLETED);

            assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        }

        @Test
        @DisplayName("새 상태가 null이면 거부한다")
        void rejectsNullStatus() {
            Work work = registeredWork();

            assertThatThrownBy(() -> work.forceStatus(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("newStatus must not be null");
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

    private static Work reconstitutedWithoutHistory(WorkStatus status) {
        return Work.reconstitute(
                new WorkId(10L),
                "에어컨 수리",
                REGISTRAR_ID,
                WORK_TYPE_ID,
                FIRST_SCHEDULE,
                CUSTOMER_INFO,
                PAYMENT_INFO,
                status,
                List.of(),
                null);
    }

    private static void assertChangedAssignment(Work work) {
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.REASSIGNED);
        assertThat(work.assignmentHistory().getLast().schedule()).isSameAs(SECOND_SCHEDULE);
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
