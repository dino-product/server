package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 지연 판정")
class WorkDelayTest {

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
    @DisplayName("대기함·완료·취소된 작업은 지연이 아니다")
    void finishedOrBacklogWorkIsNotDelayed() {
        Work backlog = Work.register(ORGANIZATION_ID, "대기 작업", MANAGER_ID, null, null, null);
        Work completed = assignedWork();
        completed.accept(START.minusSeconds(60));
        completed.start(START);
        completed.submitCompletionReport(new CompletionReport(null, null, null, null, null, null), START);
        Work cancelled = assignedWork();
        cancelled.cancel(START, MANAGER_ID, "고객 요청");

        assertThat(backlog.isDelayedAt(END)).isFalse();
        assertThat(completed.isDelayedAt(END)).isFalse();
        assertThat(cancelled.isDelayedAt(END)).isFalse();
    }

    private static Work assignedWork() {
        Work work = Work.register(ORGANIZATION_ID, "작업", MANAGER_ID, null, null, null);
        work.assign(new WorkSchedule(new TechnicianId(3L), START, TWO_HOURS), START.minusSeconds(120), MANAGER_ID);
        return work;
    }
}
