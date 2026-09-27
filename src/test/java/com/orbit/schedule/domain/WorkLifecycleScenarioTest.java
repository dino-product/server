package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Work 애그리게잇 하나로 여러 상태 전이를 이어서 수행하는 통합 시나리오. 개별 메서드의 정상·거부 조건은 {@link WorkTest}가 검증하므로, 여기서는 메서드를 실제
 * 순서대로 연쇄 호출한 뒤의 누적 최종 상태(상태·일정·배정 이력 전체·완료보고)만 단언한다.
 */
@DisplayName("작업 생애주기 통합 시나리오")
class WorkLifecycleScenarioTest {

    private static final MembershipId MANAGER_ID = new MembershipId(99L);

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final MembershipId OTHER_TECHNICIAN_ID = new MembershipId(4L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);
    private static final CustomerInfo CUSTOMER_INFO = new CustomerInfo("홍길동", "010-1234-5678", "서울시");
    private static final PaymentInfo PAYMENT_INFO = new PaymentInfo(new Money(150000L), PaymentMethod.ON_SITE_CARD);
    private static final WorkSchedule FIRST_SCHEDULE =
            new WorkSchedule(TECHNICIAN_ID, Instant.parse("2026-09-22T01:00:00Z"), Duration.ofHours(2));
    private static final WorkSchedule RESCHEDULED_FIRST_SCHEDULE =
            new WorkSchedule(TECHNICIAN_ID, Instant.parse("2026-09-22T05:00:00Z"), Duration.ofHours(2));
    private static final WorkSchedule SECOND_SCHEDULE =
            new WorkSchedule(OTHER_TECHNICIAN_ID, Instant.parse("2026-09-23T05:00:00Z"), Duration.ofMinutes(90));
    private static final Instant T1 = Instant.parse("2026-09-21T01:00:00Z");
    private static final Instant T2 = T1.plusSeconds(600);
    private static final Instant T3 = T1.plusSeconds(1200);
    private static final Instant T4 = T1.plusSeconds(1800);
    private static final Instant T5 = T1.plusSeconds(2400);
    private static final CompletionReport COMPLETION_REPORT = new CompletionReport(
            List.of("before.jpg"),
            List.of("after.jpg"),
            "필터 1개",
            "필터 교체 완료",
            new Money(150000L),
            ActualPaymentMethod.CREDIT_CARD);

    @Test
    @DisplayName("정상 경로: 등록→배정→수락→시작→완료보고")
    void happyPathFromRegistrationToCompletion() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        assertThat(work.completionReport()).contains(COMPLETION_REPORT);
        assertHistory(work, tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2), NOT_ENDED));
    }

    @Test
    @DisplayName("거절 후 다른 기사에게 다시 배정해 수락받는다")
    void rejectionThenNewAssignment() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.reject(RejectionReason.SCHEDULE_CONFLICT, T2);
        work.assign(SECOND_SCHEDULE, T3, MANAGER_ID);
        work.accept(T4);

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.REJECTED, T1, Optional.of(T2), NOT_ENDED),
                tuple(SECOND_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4), NOT_ENDED));
        assertThat(work.assignmentHistory().getFirst().rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
    }

    @Test
    @DisplayName("수락 후 다른 기사로 재배정하면 새 기사에게 다시 수락받아 완료한다")
    void reassignmentAfterAcceptanceRequiresReacceptance() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.reassign(SECOND_SCHEDULE, T3, MANAGER_ID);
        work.accept(T4);
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.REASSIGNED, T3)),
                tuple(SECOND_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4), NOT_ENDED));
    }

    @Test
    @DisplayName("수락 후 시간만 바꾸면 같은 기사에게 다시 수락받는다")
    void rescheduleAfterAcceptanceRequiresReacceptance() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.reschedule(
                RESCHEDULED_FIRST_SCHEDULE.startTime(), RESCHEDULED_FIRST_SCHEDULE.expectedDuration(), T3, MANAGER_ID);
        work.accept(T4);

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(RESCHEDULED_FIRST_SCHEDULE);
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.RESCHEDULED, T3)),
                tuple(RESCHEDULED_FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4), NOT_ENDED));
    }

    @Test
    @DisplayName("수락된 배정을 해제해 대기함으로 돌린 뒤 다시 배정한다")
    void unassignAcceptedThenAssignAgain() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.unassign(T3, MANAGER_ID);
        work.assign(SECOND_SCHEDULE, T4, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.UNASSIGNED, T3)),
                tuple(SECOND_SCHEDULE, AssignmentResult.PENDING, T4, Optional.empty(), NOT_ENDED));
    }

    @Test
    @DisplayName("응답 전 일정 변경·재배정을 거쳐 취소하면 대기 배정이 모두 각 시각에 그 방식으로 회수된다")
    void changesBeforeResponseThenCancel() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.reschedule(
                RESCHEDULED_FIRST_SCHEDULE.startTime(), RESCHEDULED_FIRST_SCHEDULE.expectedDuration(), T2, MANAGER_ID);
        work.reassign(SECOND_SCHEDULE, T3, MANAGER_ID);
        work.cancel(T4, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertThat(work.completionReport()).isEmpty();
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.WITHDRAWN,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.RESCHEDULED, T2)),
                tuple(
                        RESCHEDULED_FIRST_SCHEDULE,
                        AssignmentResult.WITHDRAWN,
                        T2,
                        Optional.of(T3),
                        endedBy(AssignmentEndReason.REASSIGNED, T3)),
                tuple(
                        SECOND_SCHEDULE,
                        AssignmentResult.WITHDRAWN,
                        T3,
                        Optional.of(T4),
                        endedBy(AssignmentEndReason.CANCELLED, T4)));
    }

    @Test
    @DisplayName("작업중 취소는 수락 결과를 그대로 두고 취소를 종료로 남긴다")
    void cancelInProgressKeepsAcceptedHistory() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.start();
        work.cancel(T5, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.CANCELLED, T5)));
    }

    @Test
    @DisplayName("수락 후 재배정한 새 기사가 거절하면 대기함으로 돌아와 다시 배정한다")
    void reassignedTechnicianRejectsThenAssignAgain() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.reassign(SECOND_SCHEDULE, T3, MANAGER_ID);
        work.reject(RejectionReason.OTHER, T4);
        work.assign(FIRST_SCHEDULE, T5, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.REASSIGNED, T3)),
                tuple(SECOND_SCHEDULE, AssignmentResult.REJECTED, T3, Optional.of(T4), NOT_ENDED),
                tuple(FIRST_SCHEDULE, AssignmentResult.PENDING, T5, Optional.empty(), NOT_ENDED));
    }

    @Test
    @DisplayName("수락된 배정을 해제한 뒤 대기함에서 취소하면 해제 기록은 그대로 남고 일정은 없다")
    void unassignAcceptedThenCancelFromBacklog() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        work.unassign(T3, MANAGER_ID);
        work.cancel(T4, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertThat(work.schedule()).isEmpty();
        assertHistory(
                work,
                tuple(
                        FIRST_SCHEDULE,
                        AssignmentResult.ACCEPTED,
                        T1,
                        Optional.of(T2),
                        endedBy(AssignmentEndReason.UNASSIGNED, T3)));
    }

    private static Work registeredWork() {
        return Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }

    private static final Optional<AssignmentEnding> NOT_ENDED = Optional.empty();

    private static Optional<AssignmentEnding> endedBy(AssignmentEndReason reason, Instant endedAt) {
        return Optional.of(new AssignmentEnding(endedAt, MANAGER_ID, reason));
    }

    /** 정상 흐름이 만든 작업은 저장값 복원 검증도 통과해야 한다. 복원 규칙을 바꿀 때 실제 흐름을 거부하는 회귀를 잡는다. */
    private static void assertRestorable(Work work) {
        List<AssignmentHistory> histories = work.assignmentHistory().stream()
                .map(history -> AssignmentHistory.restore(
                        history.schedule(),
                        history.assignedAt(),
                        history.assignedBy(),
                        history.result(),
                        history.rejectionReason().orElse(null),
                        history.decidedAt().orElse(null),
                        history.ending().orElse(null)))
                .toList();
        Work restored = Work.reconstitute(
                new WorkId(1L),
                work.organizationId(),
                work.name(),
                work.registrarId(),
                work.workType().orElse(null),
                work.schedule().orElse(null),
                work.customerInfo(),
                work.paymentInfo(),
                work.status(),
                histories,
                work.completionReport().orElse(null));

        assertThat(restored).usingRecursiveComparison().ignoringFields("id").isEqualTo(work);
    }

    private static void assertHistory(Work work, Tuple... expected) {
        assertThat(work.assignmentHistory())
                .extracting(
                        AssignmentHistory::schedule,
                        AssignmentHistory::result,
                        AssignmentHistory::assignedAt,
                        AssignmentHistory::decidedAt,
                        AssignmentHistory::ending)
                .containsExactly(expected);
        assertRestorable(work);
    }
}
