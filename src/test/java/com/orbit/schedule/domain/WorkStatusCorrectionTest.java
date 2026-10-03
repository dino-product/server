package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("작업 상태 강제 정정")
class WorkStatusCorrectionTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final MembershipId REGISTRAR_ID = new MembershipId(1L);
    private static final MembershipId MANAGER_ID = new MembershipId(99L);
    private static final MembershipId OWNER_ID = new MembershipId(98L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(3L);
    private static final WorkSchedule SCHEDULE =
            new WorkSchedule(TECHNICIAN_ID, Instant.parse("2026-09-22T01:00:00Z"), Duration.ofHours(2));
    private static final Instant T1 = Instant.parse("2026-09-21T01:00:00Z");
    private static final Instant T2 = T1.plusSeconds(600);
    private static final Instant T3 = T1.plusSeconds(1200);
    private static final Instant T4 = T1.plusSeconds(1800);
    private static final Instant T5 = T1.plusSeconds(2400);
    private static final Instant T6 = T1.plusSeconds(3000);
    private static final Instant T7 = T1.plusSeconds(3600);
    private static final CompletionReport REPORT =
            new CompletionReport(List.of("before.jpg"), List.of("after.jpg"), "필터 1개", "교체 완료", null, null);

    @Test
    @DisplayName("완료를 작업중으로 되돌리면 완료보고·완료 시각을 정정 기록에 보관하고, 기사는 보고를 다시 제출할 수 있다")
    void correctsCompletedToInProgress() {
        Work work = completedWork();

        work.correctStatus(WorkStatus.IN_PROGRESS, T5, OWNER_ID, "잘못 완료 처리");

        assertThat(work.status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(work.completionReport()).isEmpty();
        assertThat(work.completedAt()).isEmpty();
        assertThat(work.startedAt()).contains(T3);
        assertThat(work.statusCorrections())
                .containsExactly(new StatusCorrection(
                        WorkStatus.COMPLETED,
                        WorkStatus.IN_PROGRESS,
                        T5,
                        OWNER_ID,
                        "잘못 완료 처리",
                        REPORT,
                        T4,
                        null,
                        null,
                        null));
        assertRestorable(work);

        CompletionReport resubmitted = new CompletionReport(null, null, "필터 2개", "재작업 완료", null, null);
        work.submitCompletionReport(resubmitted, T6);
        assertThat(work.completionReport()).contains(resubmitted);
        assertRestorable(work);
    }

    @Test
    @DisplayName("작업중을 수락됨으로 되돌리면 시작 시각을 보관하고, 기사는 다시 시작할 수 있다")
    void correctsInProgressToAccepted() {
        Work work = acceptedWork();
        work.start(T3);

        work.correctStatus(WorkStatus.ACCEPTED, T4, OWNER_ID, "잘못 시작");

        assertThat(work.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(work.startedAt()).isEmpty();
        assertThat(work.statusCorrections().getLast().retiredStartedAt()).isEqualTo(T3);
        assertRestorable(work);

        work.start(T5);
        assertThat(work.startedAt()).contains(T5);
        assertRestorable(work);
    }

    @Test
    @DisplayName("작업중에 취소된 작업을 대기함으로 되돌리면 취소 기록·시작 시각을 보관하고 일정을 비우며, 배정 이력의 취소 종료는 흔적으로 남는다")
    void correctsCancelledInProgressToBacklog() {
        Work work = acceptedWork();
        work.start(T3);
        work.cancel(T4, MANAGER_ID, "고객 부재");
        Cancellation cancelled = work.cancellation().orElseThrow();

        work.correctStatus(WorkStatus.REGISTERED, T5, OWNER_ID, "잘못 취소");

        assertThat(work.status()).isEqualTo(WorkStatus.REGISTERED);
        assertThat(work.schedule()).isEmpty();
        assertThat(work.cancellation()).isEmpty();
        assertThat(work.startedAt()).isEmpty();
        assertThat(work.statusCorrections().getLast().retiredCancellation()).isEqualTo(cancelled);
        assertThat(work.statusCorrections().getLast().retiredStartedAt()).isEqualTo(T3);
        assertThat(work.assignmentHistory().getLast().ending())
                .map(AssignmentEnding::reason)
                .contains(AssignmentEndReason.CANCELLED);
        assertRestorable(work);
    }

    @Test
    @DisplayName("대기함으로 되돌린 작업은 다시 배정·수락·취소할 수 있고, 그 과정의 저장값도 복원된다")
    void restoredWorkFollowsNormalFlow() {
        Work work = acceptedWork();
        work.cancel(T3, MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");

        work.assign(SCHEDULE, T5, MANAGER_ID);
        work.accept(T6);
        assertRestorable(work);

        work.unassign(T7, MANAGER_ID);
        work.cancel(T7.plusSeconds(60), MANAGER_ID, "고객 재요청");
        assertThat(work.status()).isEqualTo(WorkStatus.CANCELLED);
        assertRestorable(work);
    }

    @Test
    @DisplayName("대기함에서 취소된 작업도 대기함으로 되돌리고 다시 대기함에서 취소할 수 있다")
    void correctsBacklogCancellation() {
        Work work = registeredWork();
        work.cancel(T1, MANAGER_ID, "중복 등록");

        work.correctStatus(WorkStatus.REGISTERED, T2, OWNER_ID, "중복 아님");
        assertThat(work.statusCorrections().getLast().retiredStartedAt()).isNull();
        assertRestorable(work);

        work.cancel(T3, MANAGER_ID, "다시 취소");
        assertRestorable(work);
    }

    @ParameterizedTest
    @MethodSource("disallowedCorrections")
    @DisplayName("한 단계 되돌리기가 아닌 정정은 상태 오류로 거부하고 작업을 바꾸지 않는다")
    void rejectsDisallowedCorrection(WorkStatus from, WorkStatus to) {
        Work work = workIn(from);

        assertThatThrownBy(() -> work.correctStatus(to, T7, OWNER_ID, "정정"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot correct status from " + from + " to " + to);
        assertThat(work.status()).isEqualTo(from);
        assertThat(work.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("도착 상태·시각·처리자가 없거나 사유가 비었거나 255자를 넘으면, 되돌릴 수 없는 상태라도 입력 오류다")
    void checksInputBeforeState() {
        Work work = registeredWork();

        assertThatThrownBy(() -> work.correctStatus(null, T7, OWNER_ID, "정정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("to must not be null");
        assertThatThrownBy(() -> work.correctStatus(WorkStatus.IN_PROGRESS, null, OWNER_ID, "정정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correctedAt must not be null");
        assertThatThrownBy(() -> work.correctStatus(WorkStatus.IN_PROGRESS, T7, null, "정정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correctedBy must not be null");
        assertThatThrownBy(() -> work.correctStatus(WorkStatus.IN_PROGRESS, T7, OWNER_ID, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be blank");
        assertThatThrownBy(() -> work.correctStatus(WorkStatus.IN_PROGRESS, T7, OWNER_ID, "가".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must be at most 255 characters");
        assertThat(work.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("정정 시각이 작업에 남은 가장 늦은 기록보다 앞서면 거부하고 작업을 바꾸지 않는다")
    void rejectsCorrectionBeforeLatestRecord() {
        Work work = completedWork();

        assertThatThrownBy(() -> work.correctStatus(WorkStatus.IN_PROGRESS, T4.minusSeconds(1), OWNER_ID, "정정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correctedAt must not be before the latest record");
        assertThat(work.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(work.completionReport()).contains(REPORT);
        assertThat(work.statusCorrections()).isEmpty();
    }

    @Test
    @DisplayName("되돌린 뒤의 새 배정은 정정 시각보다 앞설 수 없다")
    void rejectsAssignmentBeforeCorrection() {
        Work work = acceptedWork();
        work.cancel(T3, MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, T5, OWNER_ID, "잘못 취소");

        assertThatThrownBy(() -> work.assign(SCHEDULE, T4, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be before the latest status correction");
    }

    @Test
    @DisplayName("대기함으로 되돌린 정정이 없는데 취소 종료 뒤에 배정이 이어지거나, 대기함 작업의 최신 배정이 취소로 끝난 저장값은 거부한다")
    void rejectsCancelledAssignmentWithoutRestoration() {
        Work reassignedAfterRestore = acceptedWork();
        reassignedAfterRestore.cancel(T3, MANAGER_ID, "고객 요청");
        reassignedAfterRestore.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");
        reassignedAfterRestore.assign(SCHEDULE, T5, MANAGER_ID);
        Work restoredOnly = acceptedWork();
        restoredOnly.cancel(T3, MANAGER_ID, "고객 요청");
        restoredOnly.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");

        assertThatThrownBy(() -> restore(reassignedAfterRestore, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the latest assignment history can end by cancellation");
        assertThatThrownBy(() -> restore(restoredOnly, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REGISTERED work must not have an assignment ended by CANCELLED");
    }

    @Test
    @DisplayName("정정 기록이 시간 순서가 아닌 저장값은 거부한다")
    void rejectsCorrectionsOutOfOrder() {
        Work work = acceptedWork();
        work.start(T3);
        work.correctStatus(WorkStatus.ACCEPTED, T4, OWNER_ID, "잘못 시작");
        work.start(T5);
        work.correctStatus(WorkStatus.ACCEPTED, T6, OWNER_ID, "또 잘못 시작");
        List<StatusCorrection> reversed = new ArrayList<>(work.statusCorrections());
        Collections.reverse(reversed);

        assertThatThrownBy(() -> restore(work, reversed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status corrections must be in chronological order");
    }

    @Test
    @DisplayName("배정이 있던 채로 취소된 작업을 되돌리면 그 배정의 순번을, 대기함에서 취소된 작업이면 순번 없이 남긴다")
    void recordsRestoredAssignmentNumber() {
        Work assigned = acceptedWork();
        assigned.cancel(T3, MANAGER_ID, "고객 요청");
        Work backlog = registeredWork();
        backlog.cancel(T1, MANAGER_ID, "중복 등록");

        assigned.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");
        backlog.correctStatus(WorkStatus.REGISTERED, T2, OWNER_ID, "중복 아님");

        assertThat(assigned.statusCorrections().getLast().restoredAssignmentNumber())
                .isEqualTo(1);
        assertThat(backlog.statusCorrections().getLast().restoredAssignmentNumber())
                .isNull();
    }

    @Test
    @DisplayName("되돌린 뒤의 시작·완료보고·배정 해제·재배정·일정 변경·취소는 정정 시각보다 앞설 수 없다")
    void rejectsRecordsBeforeCorrection() {
        Work restarted = acceptedWork();
        restarted.start(T3);
        restarted.correctStatus(WorkStatus.ACCEPTED, T5, OWNER_ID, "잘못 시작");
        Work reopened = completedWork();
        reopened.correctStatus(WorkStatus.IN_PROGRESS, T5, OWNER_ID, "잘못 완료");

        assertThatThrownBy(() -> restarted.start(T4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startedAt must not be before the latest status correction");
        assertThatThrownBy(() -> restarted.unassign(T4, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endedAt must not be before the latest status correction");
        assertThatThrownBy(() -> reopened.submitCompletionReport(REPORT, T4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedAt must not be before the latest status correction");
        assertThatThrownBy(() -> restarted.reassign(
                        new WorkSchedule(new TechnicianId(4L), SCHEDULE.startTime(), SCHEDULE.expectedDuration()),
                        T4,
                        MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be before the latest status correction");
        assertThatThrownBy(() -> restarted.reschedule(
                        SCHEDULE.startTime().plusSeconds(3600), SCHEDULE.expectedDuration(), T4, MANAGER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assignedAt must not be before the latest status correction");
        assertThatThrownBy(() -> restarted.cancel(T4, MANAGER_ID, "고객 요청"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be before the latest status correction");
        assertThat(restarted.assignmentHistory()).hasSize(1);
        assertThat(restarted.status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(reopened.status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("모든 단계가 같은 시각이어도 취소 → 되돌림 → 재배정 → 수락 → 취소한 작업을 복원한다")
    void restoresSameInstantSequence() {
        Work work = registeredWork();
        work.assign(SCHEDULE, T1, MANAGER_ID);
        work.accept(T1);
        work.cancel(T1, MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, T1, OWNER_ID, "잘못 취소");
        work.assign(SCHEDULE, T1, MANAGER_ID);
        work.accept(T1);
        work.cancel(T1, MANAGER_ID, "고객 재요청");

        assertRestorable(work);
    }

    @Test
    @DisplayName("배정 중 취소를 두 번 되돌려 새로 배정한 작업은 되돌린 배정 순번을 모두 남기고 복원된다")
    void restoresTwiceRestoredWork() {
        Work work = acceptedWork();
        work.cancel(T3, MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");
        work.assign(SCHEDULE, T4, MANAGER_ID);
        work.accept(T5);
        work.cancel(T5, MANAGER_ID, "고객 재요청");
        work.correctStatus(WorkStatus.REGISTERED, T6, OWNER_ID, "또 잘못 취소");
        work.assign(SCHEDULE, T7, MANAGER_ID);

        assertThat(work.statusCorrections())
                .extracting(StatusCorrection::restoredAssignmentNumber)
                .containsExactly(1, 2);
        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertRestorable(work);
    }

    @Test
    @DisplayName("대기함에서 취소한 작업을 되돌려 배정하면 복원된다")
    void restoresBacklogRestoredThenAssignedWork() {
        Work work = registeredWork();
        work.cancel(T1, MANAGER_ID, "중복 등록");
        work.correctStatus(WorkStatus.REGISTERED, T2, OWNER_ID, "중복 아님");
        work.assign(SCHEDULE, T3, MANAGER_ID);

        assertThat(work.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
        assertRestorable(work);
    }

    @Test
    @DisplayName("되돌린 정정이 취소한 배정과 시각·처리자가 다르거나, 같은 배정을 두 번 되돌리면 복원을 거부한다")
    void rejectsMismatchedRestoration() {
        Work work = acceptedWork();
        work.cancel(T3, MANAGER_ID, "고객 요청");
        work.correctStatus(WorkStatus.REGISTERED, T4, OWNER_ID, "잘못 취소");
        StatusCorrection original = work.statusCorrections().getLast();
        StatusCorrection otherActor = new StatusCorrection(
                original.from(),
                original.to(),
                original.correctedAt(),
                original.correctedBy(),
                original.reason(),
                null,
                null,
                null,
                new Cancellation(T3, OWNER_ID, "고객 요청"),
                original.restoredAssignmentNumber());

        assertThatThrownBy(() -> restore(work, List.of(otherActor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status correction must match the cancelled assignment it restores");
        assertThatThrownBy(() -> restore(work, List.of(original, original)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status correction must restore an existing assignment once");
    }

    @Test
    @DisplayName("정정 뒤에 남은 완료·취소·시작 시각이 정정보다 앞서는 저장값은 거부한다")
    void rejectsRecordsBeforeCorrectionWhenRestoring() {
        Work completed = completedWork();
        StatusCorrection laterReopen = new StatusCorrection(
                WorkStatus.COMPLETED, WorkStatus.IN_PROGRESS, T7, OWNER_ID, "정정", REPORT, T4, null, null, null);
        Work cancelledFromBacklog = registeredWork();
        cancelledFromBacklog.cancel(T1, MANAGER_ID, "중복 등록");
        StatusCorrection laterRestore = new StatusCorrection(
                WorkStatus.CANCELLED,
                WorkStatus.REGISTERED,
                T2,
                OWNER_ID,
                "정정",
                null,
                null,
                null,
                new Cancellation(T1, MANAGER_ID, "이전 취소"),
                null);
        Work started = acceptedWork();
        started.start(T3);
        StatusCorrection laterReset = new StatusCorrection(
                WorkStatus.IN_PROGRESS, WorkStatus.ACCEPTED, T5, OWNER_ID, "정정", null, null, T2, null, null);

        assertThatThrownBy(() -> restore(completed, List.of(laterReopen)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedAt must not be before the latest status correction");
        assertThatThrownBy(() -> restore(cancelledFromBacklog, List.of(laterRestore)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be before the latest status correction");
        assertThatThrownBy(() -> restore(started, List.of(laterReset)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startedAt must not be before the status correction that cleared it");
    }

    private static Stream<Arguments> disallowedCorrections() {
        return Arrays.stream(WorkStatus.values())
                .flatMap(from -> Arrays.stream(WorkStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(arguments -> {
                    WorkStatus from = (WorkStatus) arguments.get()[0];
                    WorkStatus to = (WorkStatus) arguments.get()[1];
                    return !from.canBeCorrectedTo(to);
                });
    }

    private static Work registeredWork() {
        return Work.register(ORGANIZATION_ID, "에어컨 수리", REGISTRAR_ID, null, null, null);
    }

    private static Work acceptedWork() {
        Work work = registeredWork();
        work.assign(SCHEDULE, T1, MANAGER_ID);
        work.accept(T2);
        return work;
    }

    private static Work completedWork() {
        Work work = acceptedWork();
        work.start(T3);
        work.submitCompletionReport(REPORT, T4);
        return work;
    }

    /** 실제 전이 흐름으로 주어진 상태의 작업을 만든다. 취소는 수락된 뒤에 한다. */
    private static Work workIn(WorkStatus status) {
        return switch (status) {
            case REGISTERED -> registeredWork();
            case PENDING_ACCEPTANCE -> {
                Work work = registeredWork();
                work.assign(SCHEDULE, T1, MANAGER_ID);
                yield work;
            }
            case ACCEPTED -> acceptedWork();
            case IN_PROGRESS -> {
                Work work = acceptedWork();
                work.start(T3);
                yield work;
            }
            case COMPLETED -> completedWork();
            case CANCELLED -> {
                Work work = acceptedWork();
                work.cancel(T3, MANAGER_ID, "고객 요청");
                yield work;
            }
        };
    }

    private static void assertRestorable(Work work) {
        assertThat(restore(work, work.statusCorrections()))
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(work);
    }

    private static Work restore(Work work, List<StatusCorrection> corrections) {
        List<AssignmentHistory> histories = work.assignmentHistory().stream()
                .map(history -> AssignmentHistory.restore(
                        history.schedule(),
                        history.assignedAt(),
                        history.assignedBy(),
                        history.result(),
                        history.rejection().orElse(null),
                        history.decidedAt().orElse(null),
                        history.ending().orElse(null)))
                .toList();
        return Work.reconstitute(
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
                work.completionReport().orElse(null),
                work.startedAt().orElse(null),
                work.completedAt().orElse(null),
                work.cancellation().orElse(null),
                corrections);
    }
}
