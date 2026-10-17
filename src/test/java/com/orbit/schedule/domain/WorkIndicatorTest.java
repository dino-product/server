package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 조회 표시값(보일 일정·지연·거절 반환·지금 배정 순번)")
class WorkIndicatorTest {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(1L);
    private static final MembershipId MANAGER_ID = new MembershipId(2L);
    private static final Instant START = Instant.parse("2026-09-22T01:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final Instant END = START.plus(TWO_HOURS);

    @Test
    @DisplayName("끝나지 않은 작업은 예정 종료시각부터 지연이다")
    void activeWorkIsDelayedFromScheduledEnd() {
        Work work = assignedWork();

        assertThat(work.isDelayedAt(END.minusNanos(1_000))).isFalse();
        assertThat(work.isDelayedAt(END)).isTrue();
        work.accept(START.minusSeconds(60));
        work.start(START);
        assertThat(work.isDelayedAt(END.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("예정 종료시각이나 그 뒤에 완료한 작업은 지연이고, 그 전에 완료한 작업은 아니다")
    void completedWorkIsDelayedWhenFinishedLate() {
        Work onTime = startedWork();
        onTime.submitCompletionReport(emptyReport(), END.minusNanos(1_000));
        Work late = startedWork();
        late.submitCompletionReport(emptyReport(), END);
        Instant muchLater = END.plus(Duration.ofDays(1));

        assertThat(onTime.isDelayedAt(muchLater)).isFalse();
        assertThat(late.isDelayedAt(muchLater)).isTrue();
        assertThat(late.isDelayedAt(START)).isTrue();
    }

    @Test
    @DisplayName("대기함 작업은 보일 일정의 종료시각부터 지연이고, 시작시각이나 예상소요시간을 모르면 지연이 아니다")
    void backlogWorkIsDelayedFromShownEnd() {
        Work planned = Work.register(
                ORGANIZATION_ID,
                "시간만 정한 작업",
                MANAGER_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(null, START, TWO_HOURS));
        Work rejected = assignedWork();
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), START.minusSeconds(60));
        Work startOnly = Work.register(
                ORGANIZATION_ID,
                "시작만 정한 작업",
                MANAGER_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(new TechnicianId(3L), START, null));
        Work nothing = Work.register(ORGANIZATION_ID, "대기 작업", MANAGER_ID, REGISTERED_AT, null, null, null);
        Instant muchLater = END.plus(Duration.ofDays(1));

        assertThat(List.of(planned, rejected)).allSatisfy(work -> {
            assertThat(work.isDelayedAt(END.minusNanos(1_000))).isFalse();
            assertThat(work.isDelayedAt(END)).isTrue();
        });
        assertThat(startOnly.isDelayedAt(muchLater)).isFalse();
        assertThat(nothing.isDelayedAt(muchLater)).isFalse();
    }

    @Test
    @DisplayName("취소된 작업은 지연이 아니다")
    void cancelledWorkIsNotDelayed() {
        Work cancelled = assignedWork();
        cancelled.cancel(START, MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);

        assertThat(cancelled.isDelayedAt(END.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    @DisplayName("보일 일정은 지금 일정, 대기함 작업이면 미리 입력한 값이나 돌아온 작업의 마지막 배정 일정이다")
    void showsCurrentPlannedOrLastAssignedSchedule() {
        Work assigned = assignedWork();
        Work planned = Work.register(
                ORGANIZATION_ID,
                "기사만 정한 작업",
                MANAGER_ID,
                REGISTERED_AT,
                null,
                null,
                null,
                new PlannedSchedule(new TechnicianId(5L), null, null));
        Work rejected = assignedWork();
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), START.minusSeconds(60));
        Work unassigned = assignedWork();
        unassigned.unassign(START.minusSeconds(60), MANAGER_ID);
        Work nothing = Work.register(ORGANIZATION_ID, "대기 작업", MANAGER_ID, REGISTERED_AT, null, null, null);
        Work cancelledInBacklog = Work.register(ORGANIZATION_ID, "취소 작업", MANAGER_ID, REGISTERED_AT, null, null, null);
        cancelledInBacklog.cancel(START, MANAGER_ID, CancellationReason.CUSTOMER_REQUEST, null);
        ShownSchedule lastAssigned = new ShownSchedule(new TechnicianId(3L), START, TWO_HOURS);

        assertThat(assigned.shownSchedule()).contains(lastAssigned);
        assertThat(planned.shownSchedule()).contains(new ShownSchedule(new TechnicianId(5L), null, null));
        assertThat(planned.shownSchedule().orElseThrow().endTime()).isEmpty();
        assertThat(rejected.shownSchedule()).contains(lastAssigned);
        assertThat(unassigned.shownSchedule()).contains(lastAssigned);
        assertThat(lastAssigned.endTime()).contains(END);
        assertThat(nothing.shownSchedule()).isEmpty();
        assertThat(cancelledInBacklog.shownSchedule()).isEmpty();
    }

    @Test
    @DisplayName("최신 배정이 거절돼 대기함으로 돌아온 작업만 거절 반환이다")
    void marksReturnByRejection() {
        Work rejected = assignedWork();
        rejected.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), START.minusSeconds(60));
        Work rejectedThenReassigned = assignedWork();
        rejectedThenReassigned.reject(new Rejection(RejectionReason.OTHER, "장비 없음"), START.minusSeconds(60));
        rejectedThenReassigned.assign(
                new WorkSchedule(new TechnicianId(4L), START, TWO_HOURS), START.minusSeconds(30), MANAGER_ID);
        Work unassigned = assignedWork();
        unassigned.unassign(START.minusSeconds(60), MANAGER_ID);
        Work fresh = Work.register(ORGANIZATION_ID, "대기 작업", MANAGER_ID, REGISTERED_AT, null, null, null);

        assertThat(rejected.isReturnedByRejection()).isTrue();
        assertThat(rejectedThenReassigned.isReturnedByRejection()).isFalse();
        assertThat(unassigned.isReturnedByRejection()).isFalse();
        assertThat(fresh.isReturnedByRejection()).isFalse();
    }

    @Test
    @DisplayName("지금 배정의 순번은 배정 이력의 마지막 위치이고, 배정된 적이 없으면 0이다")
    void exposesCurrentAssignmentNumber() {
        Work work = assignedWork();
        work.reassign(new WorkSchedule(new TechnicianId(4L), START, TWO_HOURS), START.minusSeconds(60), MANAGER_ID);

        assertThat(work.currentAssignmentNumber()).isEqualTo(2);
        assertThat(Work.register(ORGANIZATION_ID, "대기 작업", MANAGER_ID, REGISTERED_AT, null, null, null)
                        .currentAssignmentNumber())
                .isZero();
    }

    private static Work startedWork() {
        Work work = assignedWork();
        work.accept(START.minusSeconds(60));
        work.start(START);
        return work;
    }

    private static CompletionReport emptyReport() {
        return new CompletionReport(null, null, null, null, null, null);
    }

    private static Work assignedWork() {
        Work work = Work.register(ORGANIZATION_ID, "작업", MANAGER_ID, REGISTERED_AT, null, null, null);
        work.assign(new WorkSchedule(new TechnicianId(3L), START, TWO_HOURS), START.minusSeconds(120), MANAGER_ID);
        return work;
    }
}
