package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("일정 겹침·동시 수행 방지 정책")
class WorkScheduleConflictPolicyTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final MembershipId TECHNICIAN_ID = new MembershipId(3L);
    private static final MembershipId OTHER_TECHNICIAN_ID = new MembershipId(4L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);
    private static final CustomerInfo CUSTOMER_INFO = new CustomerInfo("홍길동", "010-1234-5678", "서울시");
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    private static final PaymentInfo PAYMENT_INFO = new PaymentInfo(new Money(150000L), PaymentMethod.ON_SITE_CARD);
    private static final CompletionReport COMPLETION_REPORT = new CompletionReport(null, null, null, null, null, null);

    @Nested
    @DisplayName("일정 겹침")
    class FindConflictingWorks {

        @Test
        @DisplayName("기존 작업이 없으면 겹치는 작업이 없다")
        void noConflictWhenExistingEmpty() {
            assertThat(WorkScheduleConflictPolicy.findConflictingWorks(
                            scheduleOf(TECHNICIAN_ID, 10, 2), null, List.of()))
                    .isEmpty();
        }

        @Test
        @DisplayName("시간 구간이 일부 겹치면 겹치는 작업으로 찾는다")
        void detectsPartialOverlap() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 11, 2)); // 11:00~13:00

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 10, 2), existing)) // 10:00~12:00
                    .containsExactly(existing);
        }

        @Test
        @DisplayName("한 일정이 다른 일정을 완전히 포함해도 겹친다")
        void detectsContainment() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 11, 1)); // 11:00~12:00

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 10, 4), existing)) // 10:00~14:00
                    .containsExactly(existing);
        }

        @Test
        @DisplayName("시작시각과 소요시간이 같은 일정은 겹친다")
        void detectsIdenticalSchedule() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 10, 2));

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 10, 2), existing)).containsExactly(existing);
        }

        @Test
        @DisplayName("시간 구간이 떨어져 있으면 겹치지 않는다")
        void noConflictWhenApart() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 14, 1));

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 10, 1), existing)).isEmpty();
        }

        @Test
        @DisplayName("후보 시작시각이 기존 종료시각과 같으면(인접) 겹치지 않는다")
        void adjacentAfterExistingIsNotOverlap() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 9, 1)); // 9:00~10:00

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 10, 1), existing)).isEmpty();
        }

        @Test
        @DisplayName("기존 시작시각이 후보 종료시각과 같으면(인접) 겹치지 않는다")
        void adjacentBeforeExistingIsNotOverlap() {
            Work existing = pendingWork(scheduleOf(TECHNICIAN_ID, 10, 1)); // 10:00~11:00

            assertThat(conflictsOf(scheduleOf(TECHNICIAN_ID, 9, 1), existing)).isEmpty();
        }

        @Test
        @DisplayName("여러 작업 중 같은 기사의 활성 작업이면서 시간이 겹치는 작업만 골라낸다")
        void picksOnlyConflictingWorksFromMixedList() {
            Work overlappingPending = pendingWork(scheduleOf(TECHNICIAN_ID, 9, 2)); // 9:00~11:00
            Work overlappingAccepted = acceptedWork(scheduleOf(TECHNICIAN_ID, 11, 1)); // 11:00~12:00
            Work overlappingInProgress = inProgressWork(scheduleOf(TECHNICIAN_ID, 10, 1)); // 10:00~11:00
            Work otherTechnician = pendingWork(scheduleOf(OTHER_TECHNICIAN_ID, 10, 2));
            Work apart = pendingWork(scheduleOf(TECHNICIAN_ID, 15, 1));
            Work completed = completedWork(scheduleOf(TECHNICIAN_ID, 10, 2));
            Work cancelled = cancelledWork(scheduleOf(TECHNICIAN_ID, 10, 2));
            Work unassigned = Work.register(ORGANIZATION_ID, "미배정", REGISTRAR_ID, null, null, null);

            List<Work> conflicts = WorkScheduleConflictPolicy.findConflictingWorks(
                    scheduleOf(TECHNICIAN_ID, 10, 2), // 10:00~12:00
                    null,
                    List.of(
                            overlappingPending,
                            otherTechnician,
                            apart,
                            overlappingAccepted,
                            completed,
                            cancelled,
                            unassigned,
                            overlappingInProgress));

            assertThat(conflicts).containsExactly(overlappingPending, overlappingAccepted, overlappingInProgress);
        }

        @Test
        @DisplayName("일정을 바꾸는 작업 자신은 겹침에서 제외한다")
        void excludesTargetWork() {
            Work target = pendingWork(scheduleOf(TECHNICIAN_ID, 10, 2));
            Work other = pendingWork(scheduleOf(TECHNICIAN_ID, 11, 1));

            List<Work> conflicts = WorkScheduleConflictPolicy.findConflictingWorks(
                    scheduleOf(TECHNICIAN_ID, 11, 2), target, List.of(target, other));

            assertThat(conflicts).containsExactly(other);
        }

        @Test
        @DisplayName("같은 식별자로 다시 조회한 작업 자신도 제외한다")
        void excludesTargetWorkBySameId() {
            Work target = reconstitutedPendingWork(new WorkId(10L), scheduleOf(TECHNICIAN_ID, 10, 2));
            Work reloadedTarget = reconstitutedPendingWork(new WorkId(10L), scheduleOf(TECHNICIAN_ID, 10, 2));

            assertThat(WorkScheduleConflictPolicy.findConflictingWorks(
                            scheduleOf(TECHNICIAN_ID, 11, 2), target, List.of(reloadedTarget)))
                    .isEmpty();
        }

        @Test
        @DisplayName("후보가 null이면 거부한다")
        void rejectsNullCandidate() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.findConflictingWorks(null, null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("candidate must not be null");
        }

        @Test
        @DisplayName("기존 작업 목록이 null이면 거부한다")
        void rejectsNullExistingWorks() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.findConflictingWorks(
                            scheduleOf(TECHNICIAN_ID, 10, 2), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("existingWorks must not be null");
        }
    }

    @Nested
    @DisplayName("동시 수행")
    class HasConcurrentInProgress {

        @Test
        @DisplayName("같은 기사에게 작업중인 작업이 있으면 동시 수행이다")
        void detectsConcurrentInProgress() {
            Work work = inProgressWork(scheduleOf(TECHNICIAN_ID, 10, 2));

            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(TECHNICIAN_ID, List.of(work)))
                    .isTrue();
        }

        @Test
        @DisplayName("다른 기사의 작업중 작업은 동시 수행으로 보지 않는다")
        void ignoresOtherTechnicianInProgressWork() {
            Work otherTechnicianWork = inProgressWork(scheduleOf(OTHER_TECHNICIAN_ID, 10, 2));
            Work acceptedWork = acceptedWork(scheduleOf(TECHNICIAN_ID, 13, 1));

            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(
                            TECHNICIAN_ID, List.of(otherTechnicianWork, acceptedWork)))
                    .isFalse();
        }

        @Test
        @DisplayName("작업중이 아닌 작업만 있으면 동시 수행이 아니다")
        void ignoresNonInProgressWorks() {
            Work accepted = acceptedWork(scheduleOf(TECHNICIAN_ID, 10, 2));
            Work completed = completedWork(scheduleOf(TECHNICIAN_ID, 8, 1));

            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(TECHNICIAN_ID, List.of(accepted, completed)))
                    .isFalse();
        }

        @Test
        @DisplayName("목록이 비어 있으면 동시 수행이 아니다")
        void noConcurrentWhenEmpty() {
            assertThat(WorkScheduleConflictPolicy.hasConcurrentInProgress(TECHNICIAN_ID, List.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("기사가 null이면 거부한다")
        void rejectsNullTechnician() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.hasConcurrentInProgress(null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("technicianId must not be null");
        }

        @Test
        @DisplayName("목록이 null이면 거부한다")
        void rejectsNullList() {
            assertThatThrownBy(() -> WorkScheduleConflictPolicy.hasConcurrentInProgress(TECHNICIAN_ID, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("works must not be null");
        }
    }

    private static List<Work> conflictsOf(WorkSchedule candidate, Work existing) {
        return WorkScheduleConflictPolicy.findConflictingWorks(candidate, null, List.of(existing));
    }

    private static WorkSchedule scheduleOf(MembershipId technicianId, int hour, int durationHours) {
        return new WorkSchedule(
                technicianId,
                Instant.parse("2026-09-22T00:00:00Z").plus(Duration.ofHours(hour)),
                Duration.ofHours(durationHours));
    }

    private static Work pendingWork(WorkSchedule schedule) {
        Work work = Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, WORK_TYPE_ID, CUSTOMER_INFO, PAYMENT_INFO);
        work.assign(schedule, NOW);
        return work;
    }

    private static Work acceptedWork(WorkSchedule schedule) {
        Work work = pendingWork(schedule);
        work.accept(NOW);
        return work;
    }

    private static Work inProgressWork(WorkSchedule schedule) {
        Work work = acceptedWork(schedule);
        work.start();
        return work;
    }

    private static Work completedWork(WorkSchedule schedule) {
        Work work = inProgressWork(schedule);
        work.submitCompletionReport(COMPLETION_REPORT);
        return work;
    }

    private static Work cancelledWork(WorkSchedule schedule) {
        Work work = pendingWork(schedule);
        work.cancel(NOW);
        return work;
    }

    private static Work reconstitutedPendingWork(WorkId id, WorkSchedule schedule) {
        return Work.reconstitute(
                id,
                ORGANIZATION_ID,
                "에어컨 수리",
                REGISTRAR_ID,
                WORK_TYPE_ID,
                schedule,
                CUSTOMER_INFO,
                PAYMENT_INFO,
                WorkStatus.PENDING_ACCEPTANCE,
                List.of(AssignmentHistory.restore(schedule, NOW, AssignmentResult.PENDING, null, null)),
                null);
    }
}
