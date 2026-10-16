package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업의 등록 시각과 대기함 방치")
class WorkRegistrationTimeTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final TechnicianId TECHNICIAN = new TechnicianId(3L);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T01:00:00Z");
    private static final WorkSchedule SCHEDULE =
            new WorkSchedule(TECHNICIAN, Instant.parse("2026-10-05T01:00:00Z"), Duration.ofHours(2));

    @Test
    @DisplayName("등록 시각을 마이크로초로 잘라 남긴다")
    void keepsRegisteredAtInMicros() {
        Work work = register(REGISTERED_AT.plusNanos(123_456_789));

        assertThat(work.registeredAt()).isEqualTo(REGISTERED_AT.plusNanos(123_456_000));
    }

    @Test
    @DisplayName("등록 시각 없이 등록할 수 없다")
    void rejectsMissingRegisteredAt() {
        assertThatThrownBy(() -> register(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registeredAt must not be null");
    }

    @Test
    @DisplayName("등록보다 앞선 시각으로 배정하거나 대기함에서 취소할 수 없다")
    void rejectsRecordsBeforeRegistration() {
        Work work = register(REGISTERED_AT);
        Instant before = REGISTERED_AT.minusSeconds(1);

        assertThatThrownBy(() -> work.assign(SCHEDULE, before, REGISTRAR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be before the registration");
        assertThatThrownBy(() -> work.cancel(before, REGISTRAR, CancellationReason.DUPLICATE_REGISTRATION, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be before the registration");
        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
    }

    @Test
    @DisplayName("대기함에 들어온 시각은 등록 작업이면 등록 시각, 돌아온 작업이면 거절·해제·탈퇴 반환 시각이다")
    void tracksWhenWorkEnteredBacklog() {
        Work registered = register(REGISTERED_AT);
        Work rejected = register(REGISTERED_AT);
        rejected.assign(SCHEDULE, REGISTERED_AT.plusSeconds(10), REGISTRAR);
        rejected.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), REGISTERED_AT.plusSeconds(20));
        Work unassigned = register(REGISTERED_AT);
        unassigned.assign(SCHEDULE, REGISTERED_AT.plusSeconds(10), REGISTRAR);
        unassigned.accept(REGISTERED_AT.plusSeconds(20));
        unassigned.unassign(REGISTERED_AT.plusSeconds(30), REGISTRAR);

        Work released = register(REGISTERED_AT);
        released.assign(SCHEDULE, REGISTERED_AT.plusSeconds(10), REGISTRAR);
        released.releaseForWithdrawal(REGISTERED_AT.plusSeconds(40));

        assertThat(registered.backlogEnteredAt()).contains(REGISTERED_AT);
        assertThat(rejected.backlogEnteredAt()).contains(REGISTERED_AT.plusSeconds(20));
        assertThat(unassigned.backlogEnteredAt()).contains(REGISTERED_AT.plusSeconds(30));
        assertThat(released.backlogEnteredAt()).contains(REGISTERED_AT.plusSeconds(40));
    }

    @Test
    @DisplayName("대기함에 없는 작업은 대기함에 들어온 시각이 없고 방치되지 않는다")
    void hasNoBacklogEntryOutsideBacklog() {
        Work assigned = register(REGISTERED_AT);
        assigned.assign(SCHEDULE, REGISTERED_AT.plusSeconds(10), REGISTRAR);
        Work cancelled = register(REGISTERED_AT);
        cancelled.cancel(REGISTERED_AT, REGISTRAR, CancellationReason.DUPLICATE_REGISTRATION, null);
        Instant muchLater = REGISTERED_AT.plus(Duration.ofDays(30));

        assertThat(assigned.backlogEnteredAt()).isEmpty();
        assertThat(cancelled.backlogEnteredAt()).isEmpty();
        assertThat(assigned.isStaleInBacklogAt(muchLater)).isFalse();
        assertThat(cancelled.isStaleInBacklogAt(muchLater)).isFalse();
    }

    @Test
    @DisplayName("대기함에 들어온 지 3일(72시간)이 되면 방치로 본다")
    void becomesStaleThreeDaysAfterEnteringBacklog() {
        Work work = register(REGISTERED_AT);
        Instant threeDaysLater = REGISTERED_AT.plus(Work.BACKLOG_STALE_AFTER);

        assertThat(Work.BACKLOG_STALE_AFTER).isEqualTo(Duration.ofHours(72));
        assertThat(work.isStaleInBacklogAt(threeDaysLater.minusNanos(1_000))).isFalse();
        assertThat(work.isStaleInBacklogAt(threeDaysLater)).isTrue();
    }

    @Test
    @DisplayName("거절로 돌아온 작업은 돌아온 시각부터 다시 센다")
    void restartsStaleCountWhenReturned() {
        Work work = register(REGISTERED_AT);
        Instant rejectedAt = REGISTERED_AT.plus(Duration.ofDays(2));
        work.assign(SCHEDULE, rejectedAt.minusSeconds(10), REGISTRAR);
        work.reject(new Rejection(RejectionReason.SCHEDULE_CONFLICT, null), rejectedAt);

        assertThat(work.isStaleInBacklogAt(REGISTERED_AT.plus(Duration.ofDays(3))))
                .isFalse();
        assertThat(work.isStaleInBacklogAt(rejectedAt.plus(Duration.ofDays(3)))).isTrue();
    }

    @Test
    @DisplayName("등록보다 앞선 배정·취소가 있는 저장값은 복원하지 않는다")
    void rejectsStoredRecordsBeforeRegistration() {
        AssignmentHistory early = new AssignmentHistory(SCHEDULE, REGISTERED_AT.minusSeconds(1), REGISTRAR);
        Cancellation earlyCancellation = new Cancellation(
                REGISTERED_AT.minusSeconds(1), REGISTRAR, CancellationReason.DUPLICATE_REGISTRATION, null);

        assertThatThrownBy(() -> restore(WorkStatus.PENDING_ACCEPTANCE, SCHEDULE, List.of(early), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignment histories must not precede the registration");
        assertThatThrownBy(() -> restore(WorkStatus.CANCELLED, null, List.of(), earlyCancellation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not precede the registration");
    }

    @Test
    @DisplayName("저장값의 등록 시각을 그대로 복원한다")
    void restoresRegisteredAt() {
        Work work = restore(WorkStatus.REGISTERED, null, List.of(), null);

        assertThat(work.registeredAt()).isEqualTo(REGISTERED_AT);
    }

    private static Work register(Instant registeredAt) {
        return Work.register(ORGANIZATION, "보일러 점검", REGISTRAR, registeredAt, null, null, null);
    }

    private static Work restore(
            WorkStatus status, WorkSchedule schedule, List<AssignmentHistory> histories, Cancellation cancellation) {
        return Work.reconstitute(
                new WorkId(10L),
                ORGANIZATION,
                "보일러 점검",
                REGISTRAR,
                REGISTERED_AT,
                null,
                schedule,
                null,
                null,
                status,
                histories,
                null,
                null,
                null,
                cancellation,
                List.of());
    }
}
