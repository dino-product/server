package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
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

    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final MembershipId OTHER_TECHNICIAN_ID = new MembershipId(4L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);
    private static final CustomerInfo CUSTOMER_INFO = new CustomerInfo("홍길동", "010-1234-5678", "서울시");
    private static final PaymentInfo PAYMENT_INFO =
            new PaymentInfo(new BigDecimal("150000"), PaymentMethod.ON_SITE_CARD);
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
            new BigDecimal("150000"),
            ActualPaymentMethod.CREDIT_CARD);

    @Test
    @DisplayName("정상 경로: 등록→배정→수락→시작→완료보고")
    void happyPathFromRegistrationToCompletion() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.accept(T2);
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        assertThat(work.completionReport()).contains(COMPLETION_REPORT);
        assertHistory(work, tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2)));
    }

    @Test
    @DisplayName("거절 후 다른 기사에게 다시 배정해 수락받는다")
    void rejectionThenNewAssignment() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.reject(RejectionReason.SCHEDULE_CONFLICT, T2);
        work.assign(SECOND_SCHEDULE, T3);
        work.accept(T4);

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.REJECTED, T1, Optional.of(T2)),
                tuple(SECOND_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4)));
        assertThat(work.assignmentHistory().getFirst().rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
    }

    @Test
    @DisplayName("수락 후 다른 기사로 재배정하면 새 기사에게 다시 수락받아 완료한다")
    void reassignmentAfterAcceptanceRequiresReacceptance() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.accept(T2);
        work.reassign(SECOND_SCHEDULE, T3);
        work.accept(T4);
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2)),
                tuple(SECOND_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4)));
    }

    @Test
    @DisplayName("수락 후 시간만 바꾸면 같은 기사에게 다시 수락받는다")
    void rescheduleAfterAcceptanceRequiresReacceptance() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.accept(T2);
        work.reschedule(RESCHEDULED_FIRST_SCHEDULE, T3);
        work.accept(T4);

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(RESCHEDULED_FIRST_SCHEDULE);
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2)),
                tuple(RESCHEDULED_FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T3, Optional.of(T4)));
    }

    @Test
    @DisplayName("수락된 배정을 해제해 대기함으로 돌린 뒤 다시 배정한다")
    void unassignAcceptedThenAssignAgain() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.accept(T2);
        work.unassign(T3);
        work.assign(SECOND_SCHEDULE, T4);

        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2)),
                tuple(SECOND_SCHEDULE, AssignmentResult.PENDING, T4, Optional.empty()));
    }

    @Test
    @DisplayName("응답 전 일정 변경·재배정을 거쳐 취소하면 대기 배정이 모두 각 시각으로 마감된다")
    void changesBeforeResponseThenCancel() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.reschedule(RESCHEDULED_FIRST_SCHEDULE, T2);
        work.reassign(SECOND_SCHEDULE, T3);
        work.cancel(T4);

        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertThat(work.completionReport()).isEmpty();
        assertHistory(
                work,
                tuple(FIRST_SCHEDULE, AssignmentResult.REASSIGNED, T1, Optional.of(T2)),
                tuple(RESCHEDULED_FIRST_SCHEDULE, AssignmentResult.REASSIGNED, T2, Optional.of(T3)),
                tuple(SECOND_SCHEDULE, AssignmentResult.REASSIGNED, T3, Optional.of(T4)));
    }

    @Test
    @DisplayName("작업중 취소는 수락 이력을 그대로 남긴다")
    void cancelInProgressKeepsAcceptedHistory() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE, T1);
        work.accept(T2);
        work.start();
        work.cancel(T5);

        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertHistory(work, tuple(FIRST_SCHEDULE, AssignmentResult.ACCEPTED, T1, Optional.of(T2)));
    }

    private static Work registeredWork() {
        return Work.register("에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }

    private static void assertHistory(Work work, Tuple... expected) {
        assertThat(work.assignmentHistory())
                .extracting(
                        AssignmentHistory::schedule,
                        AssignmentHistory::result,
                        AssignmentHistory::assignedAt,
                        AssignmentHistory::decidedAt)
                .containsExactly(expected);
    }
}
