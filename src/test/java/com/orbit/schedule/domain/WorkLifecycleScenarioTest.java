package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Work 애그리게잇 하나로 여러 상태전이를 이어서 수행하는 통합 시나리오. 개별 메서드의 정상/거부 조건은
 * {@link WorkTest}에서 검증하므로, 여기서는 그 메서드들을 실제 순서대로 연쇄 호출했을 때 애그리게잇 전체
 * 상태(schedule·assignmentHistory·completionReport·status)가 일관되게 누적·정리되는지에 집중한다.
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
            new WorkSchedule(TECHNICIAN_ID, LocalDateTime.of(2026, 9, 22, 10, 0), Duration.ofHours(2));
    private static final WorkSchedule SECOND_SCHEDULE =
            new WorkSchedule(OTHER_TECHNICIAN_ID, LocalDateTime.of(2026, 9, 23, 14, 0), Duration.ofMinutes(90));
    private static final CompletionReport COMPLETION_REPORT = new CompletionReport(
            List.of("before.jpg"),
            List.of("after.jpg"),
            "필터 1개",
            "필터 교체 완료",
            new BigDecimal("150000"),
            ActualPaymentMethod.CREDIT_CARD);

    @Test
    @DisplayName("정상 경로: 등록→배정→수락→시작→완료보고까지 이어서 진행한다")
    void happyPathFromRegistrationToCompletion() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE);
        work.accept();
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(FIRST_SCHEDULE);
        assertThat(work.completionReport()).contains(COMPLETION_REPORT);
        assertThat(work.assignmentHistory()).hasSize(1);
        assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
    }

    @Test
    @DisplayName("거절 후 재배정: 첫 배정이 거절되면 새 배정으로 다시 진행할 수 있다")
    void rejectionThenReassignmentPath() {
        Work work = registeredWork();

        work.assign(FIRST_SCHEDULE);
        work.reject(RejectionReason.SCHEDULE_CONFLICT);
        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.schedule()).isEmpty();

        work.assign(SECOND_SCHEDULE);
        work.accept();

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertThat(work.assignmentHistory()).hasSize(2);
        assertThat(work.assignmentHistory().get(0).result()).isEqualTo(AssignmentResult.REJECTED);
        assertThat(work.assignmentHistory().get(0).rejectionReason()).contains(RejectionReason.SCHEDULE_CONFLICT);
        assertThat(work.assignmentHistory().get(1).result()).isEqualTo(AssignmentResult.ACCEPTED);
    }

    @Nested
    @DisplayName("취소: 등록부터 작업중까지 각 단계에서 자연스러운 흐름으로 도달해 취소한다")
    class CancellationAtEachStage {

        @Test
        @DisplayName("등록 상태에서 취소한다")
        void cancelsFromRegistered() {
            Work work = registeredWork();

            work.cancel();

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        }

        @Test
        @DisplayName("수락 대기 상태에서 취소한다")
        void cancelsFromPendingAcceptance() {
            Work work = registeredWork();
            work.assign(FIRST_SCHEDULE);

            work.cancel();

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
            assertThat(work.assignmentHistory()).hasSize(1);
        }

        @Test
        @DisplayName("수락 상태에서 취소한다")
        void cancelsFromAccepted() {
            Work work = registeredWork();
            work.assign(FIRST_SCHEDULE);
            work.accept();

            work.cancel();

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
            assertThat(work.assignmentHistory().getFirst().result()).isEqualTo(AssignmentResult.ACCEPTED);
        }

        @Test
        @DisplayName("작업중 상태에서 취소한다")
        void cancelsFromInProgress() {
            Work work = registeredWork();
            work.assign(FIRST_SCHEDULE);
            work.accept();
            work.start();

            work.cancel();

            assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        }
    }

    @Test
    @DisplayName("관리자 강제변경: 진행중 작업을 대기함으로 역방향 전이시킨 뒤 다시 정상 흐름을 이어간다")
    void adminForceStatusReversesInProgressWorkThenResumesNormalFlow() {
        Work work = registeredWork();
        work.assign(FIRST_SCHEDULE);
        work.accept();
        work.start();
        assertThat(work.status()).isEqualTo(WorkStatus.IN_PROGRESS);

        // IN_PROGRESS -> REGISTERED는 화이트리스트에 없는 역방향 전이이며 forceStatus로만 가능하다.
        work.forceStatus(WorkStatus.REGISTERED);

        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.schedule()).isEmpty();
        assertThat(work.assignmentHistory()).hasSize(1);

        work.assign(SECOND_SCHEDULE);
        work.accept();
        work.start();
        work.submitCompletionReport(COMPLETION_REPORT);

        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.schedule()).contains(SECOND_SCHEDULE);
        assertThat(work.assignmentHistory()).hasSize(2);
    }

    private static Work registeredWork() {
        return Work.register("에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }
}
