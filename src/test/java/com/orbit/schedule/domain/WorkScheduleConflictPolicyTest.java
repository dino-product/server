package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("일정 겹침·동시 수행 방지 정책")
class WorkScheduleConflictPolicyTest {

    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final MembershipId OTHER_TECHNICIAN_ID = new MembershipId(4L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);
    private static final CustomerInfo CUSTOMER_INFO = new CustomerInfo("홍길동", "010-1234-5678", "서울시");
    private static final PaymentInfo PAYMENT_INFO =
            new PaymentInfo(new BigDecimal("150000"), PaymentMethod.ON_SITE_CARD);

    private static WorkSchedule scheduleOf(MembershipId technicianId, int hour, int durationHours) {
        return new WorkSchedule(technicianId, LocalDateTime.of(2026, 9, 22, hour, 0), Duration.ofHours(durationHours));
    }

    @Nested
    @DisplayName("overlaps")
    class Overlaps {

        @Test
        @DisplayName("기존 일정 목록이 비어 있으면 겹치지 않는다")
        void noConflictWhenExistingEmpty() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 2);

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("시간 구간이 겹치면 겹침으로 판정한다")
        void detectsOverlap() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 2); // 10:00~12:00
            WorkSchedule existing = scheduleOf(TECHNICIAN_ID, 11, 2); // 11:00~13:00

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isTrue();
        }

        @Test
        @DisplayName("한 일정이 다른 일정을 완전히 포함해도 겹침으로 판정한다")
        void detectsContainment() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 4); // 10:00~14:00
            WorkSchedule existing = scheduleOf(TECHNICIAN_ID, 11, 1); // 11:00~12:00

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isTrue();
        }

        @Test
        @DisplayName("시간 구간이 떨어져 있으면 겹치지 않는다")
        void noConflictWhenApart() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 1); // 10:00~11:00
            WorkSchedule existing = scheduleOf(TECHNICIAN_ID, 14, 1); // 14:00~15:00

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isFalse();
        }

        @Test
        @DisplayName("후보 시작시각이 기존 일정 종료시각과 같으면(인접) 겹치지 않는다")
        void adjacentAfterExistingIsNotOverlap() {
            WorkSchedule existing = scheduleOf(TECHNICIAN_ID, 9, 1); // 9:00~10:00
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 1); // 10:00~11:00

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isFalse();
        }

        @Test
        @DisplayName("기존 일정 시작시각이 후보 종료시각과 같으면(인접) 겹치지 않는다")
        void adjacentBeforeExistingIsNotOverlap() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 9, 1); // 9:00~10:00
            WorkSchedule existing = scheduleOf(TECHNICIAN_ID, 10, 1); // 10:00~11:00

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isFalse();
        }

        @Test
        @DisplayName("다른 기사의 일정은 시간이 겹쳐도 무시한다")
        void ignoresOtherTechnicianSchedules() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 2);
            WorkSchedule existing = scheduleOf(OTHER_TECHNICIAN_ID, 10, 2);

            assertThat(WorkScheduleConflictPolicy.overlaps(candidate, List.of(existing)))
                    .isFalse();
        }

        @Test
        @DisplayName("후보가 null이면 거부한다")
        void rejectsNullCandidate() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.overlaps(null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("candidate must not be null");
        }

        @Test
        @DisplayName("기존 일정 목록이 null이면 거부한다")
        void rejectsNullExistingSchedules() {
            WorkSchedule candidate = scheduleOf(TECHNICIAN_ID, 10, 2);

            assertThatThrownBy(() -> WorkScheduleConflictPolicy.overlaps(candidate, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("existingSchedules must not be null");
        }
    }

    @Nested
    @DisplayName("hasConcurrentInProgress")
    class HasConcurrentInProgress {

        @Test
        @DisplayName("진행중인 작업이 있으면 동시진행으로 판정한다")
        void detectsConcurrentInProgress() {
            Work work = inProgressWork();

            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(List.of(work)))
                    .isTrue();
        }

        @Test
        @DisplayName("목록이 비어 있으면 동시진행이 아니다")
        void noConcurrentWhenEmpty() {
            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(List.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("진행중이 아닌 작업만 있으면 동시진행이 아니다")
        void ignoresNonInProgressWorks() {
            Work work = acceptedWork();

            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(List.of(work)))
                    .isFalse();
        }

        @Test
        @DisplayName("목록이 null이면 거부한다")
        void rejectsNullList() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.hasConcurrentInProgress(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("inProgressWorks must not be null");
        }
    }

    private static Work registeredWork() {
        return Work.register("에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
    }

    private static Work acceptedWork() {
        Work work = registeredWork();
        work.assign(scheduleOf(TECHNICIAN_ID, 10, 2));
        work.accept();
        return work;
    }

    private static Work inProgressWork() {
        Work work = acceptedWork();
        work.start();
        return work;
    }
}
