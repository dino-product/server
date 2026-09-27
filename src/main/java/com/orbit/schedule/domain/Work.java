package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 작업 등록부터 배정, 수행, 완료까지의 생애주기를 관리하는 애그리게잇 루트. 시작·완료 시각은 담당 기사의 실제 수행 기록이며, 다른 기록 시각처럼 저장소 정밀도인
 * 마이크로초로 잘라 둔다.
 */
public final class Work {

    /** 작업명 최대 길이. 목록·타임테이블 카드에 보이는 짧은 제목이다. */
    public static final int MAX_NAME_LENGTH = 100;

    private final WorkId id;
    private final OrganizationId organizationId;
    private String name;
    private final MembershipId registrarId;
    private WorkTypeId workType;
    private WorkSchedule schedule;
    private CustomerInfo customerInfo;
    private PaymentInfo paymentInfo;
    private WorkStatus status;
    private final List<AssignmentHistory> assignmentHistory;
    private CompletionReport completionReport;
    private Instant startedAt;
    private Instant completedAt;
    private Cancellation cancellation;
    private final List<StatusCorrection> statusCorrections;

    private Work(
            WorkId id,
            OrganizationId organizationId,
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            WorkSchedule schedule,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo,
            WorkStatus status,
            List<AssignmentHistory> assignmentHistory,
            CompletionReport completionReport,
            Instant startedAt,
            Instant completedAt,
            Cancellation cancellation,
            List<StatusCorrection> statusCorrections) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }
        requireName(name);
        if (registrarId == null) {
            throw new IllegalArgumentException("registrarId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.registrarId = registrarId;
        this.workType = workType;
        this.schedule = schedule;
        this.customerInfo = orEmpty(customerInfo);
        this.paymentInfo = orEmpty(paymentInfo);
        this.status = status;
        this.assignmentHistory = assignmentHistory == null ? new ArrayList<>() : new ArrayList<>(assignmentHistory);
        this.completionReport = completionReport;
        this.startedAt = truncateToMicros(startedAt);
        this.completedAt = truncateToMicros(completedAt);
        this.cancellation = cancellation;
        this.statusCorrections = statusCorrections == null ? new ArrayList<>() : new ArrayList<>(statusCorrections);
    }

    public static Work register(
            OrganizationId organizationId,
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo) {
        return new Work(
                null,
                organizationId,
                name,
                registrarId,
                workType,
                null,
                customerInfo,
                paymentInfo,
                WorkStatus.REGISTERED,
                List.of(),
                null,
                null,
                null,
                null,
                List.of());
    }

    public static Work reconstitute(
            WorkId id,
            OrganizationId organizationId,
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            WorkSchedule schedule,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo,
            WorkStatus status,
            List<AssignmentHistory> assignmentHistory,
            CompletionReport completionReport,
            Instant startedAt,
            Instant completedAt,
            Cancellation cancellation,
            List<StatusCorrection> statusCorrections) {
        Work work = new Work(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                name,
                registrarId,
                workType,
                schedule,
                customerInfo,
                paymentInfo,
                status,
                assignmentHistory,
                completionReport,
                startedAt,
                completedAt,
                cancellation,
                statusCorrections);
        work.requireConsistentState();
        return work;
    }

    public Optional<WorkId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public String name() {
        return name;
    }

    public MembershipId registrarId() {
        return registrarId;
    }

    public Optional<WorkTypeId> workType() {
        return Optional.ofNullable(workType);
    }

    public Optional<WorkSchedule> schedule() {
        return Optional.ofNullable(schedule);
    }

    public CustomerInfo customerInfo() {
        return customerInfo;
    }

    public PaymentInfo paymentInfo() {
        return paymentInfo;
    }

    public WorkStatus status() {
        return status;
    }

    public List<AssignmentHistory> assignmentHistory() {
        return List.copyOf(assignmentHistory);
    }

    public Optional<CompletionReport> completionReport() {
        return Optional.ofNullable(completionReport);
    }

    /** 담당 기사가 작업을 시작한 시각. 작업중·완료된 작업과 작업중에 취소된 작업에만 있다. */
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /** 완료보고를 제출해 작업을 마친 시각. 완료된 작업에만 있다. */
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    /** 작업을 취소한 기록(시각·처리자·사유). 취소된 작업에만 있다. */
    public Optional<Cancellation> cancellation() {
        return Optional.ofNullable(cancellation);
    }

    /** 총관리자가 상태를 되돌린 기록. 오래된 것부터 쌓인다. */
    public List<StatusCorrection> statusCorrections() {
        return List.copyOf(statusCorrections);
    }

    /**
     * 주어진 시각에 지연된 작업인지. 아직 끝나지 않은(수락대기·수락됨·작업중) 작업의 예정 종료시각이 그 시각이거나 이미 지났으면 지연이다. 지연은 상태가 아니라 조회
     * 시각으로 계산하는 표시값이다.
     */
    public boolean isDelayedAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status.isActive() && !now.isBefore(schedule.endTime());
    }

    /** 기사가 거절해 대기함으로 돌아온 작업인지. 대기함 작업의 최신 배정이 거절로 끝났으면 그렇다. 대기함 목록의 거절 반환 표시와 작업 목록의 거절 필터가 쓴다. */
    public boolean isReturnedByRejection() {
        return status == WorkStatus.REGISTERED
                && !assignmentHistory.isEmpty()
                && assignmentHistory.getLast().result() == AssignmentResult.REJECTED;
    }

    /** 대기함 작업을 기사·일정에 배정하고 수락을 기다린다. 배정한 관리자(조직 소속)를 이력에 남긴다. */
    public void assign(WorkSchedule newSchedule, Instant assignedAt, MembershipId assignedBy) {
        requireStatus(WorkStatus.REGISTERED, "assign");
        requireSchedule(newSchedule);
        // 검증을 모두 마친 뒤 반영해, 예외가 나도 작업이 부분적으로 바뀌지 않게 한다.
        AssignmentHistory newAssignment = new AssignmentHistory(newSchedule, assignedAt, assignedBy);
        requireNotBeforeLatestAssignment(assignedAt, "assignedAt");
        requireNotBeforeLatestCorrection(assignedAt, "assignedAt");
        WorkStatus nextStatus = status.transitionTo(WorkStatus.PENDING_ACCEPTANCE);
        schedule = newSchedule;
        assignmentHistory.add(newAssignment);
        status = nextStatus;
    }

    public void accept(Instant decidedAt) {
        requireStatus(WorkStatus.PENDING_ACCEPTANCE, "accept");
        // 전이를 먼저 계산해, 이력을 바꾼 뒤 전이가 실패해 둘이 어긋나는 일이 없게 한다.
        WorkStatus nextStatus = status.transitionTo(WorkStatus.ACCEPTED);
        latestAssignment().accept(decidedAt);
        status = nextStatus;
    }

    public void reject(Rejection rejection, Instant decidedAt) {
        requireStatus(WorkStatus.PENDING_ACCEPTANCE, "reject");
        WorkStatus nextStatus = status.transitionTo(WorkStatus.REGISTERED);
        latestAssignment().reject(rejection, decidedAt);
        schedule = null;
        status = nextStatus;
    }

    /** 담당기사를 바꾼다. 수락 이후라도 새 기사에게 다시 수락받는다. 바꾼 관리자를 이전 배정의 종료와 새 배정에 남긴다. */
    public void reassign(WorkSchedule newSchedule, Instant changedAt, MembershipId changedBy) {
        requireChangeableAssignment("reassign");
        requireSchedule(newSchedule);
        if (newSchedule.technicianId().equals(schedule.technicianId())) {
            throw new SameTechnicianException();
        }
        changeAssignment(newSchedule, changedAt, changedBy, AssignmentEndReason.REASSIGNED);
    }

    /**
     * 담당기사는 그대로 두고 시작시각·예상소요시간을 바꾼다. 기사가 수락한 것은 원래 시간이므로 다시 수락받는다. 현재 기사로 일정을 만들어야 하므로 상태를 시간 입력보다
     * 먼저 확인한다. 입력을 상태보다 먼저 거르려면 호출자가 {@link WorkSchedule#requireValidTime}으로 미리 검증한다.
     */
    public void reschedule(
            Instant newStartTime, Duration newExpectedDuration, Instant changedAt, MembershipId changedBy) {
        requireChangeableAssignment("reschedule");
        WorkSchedule newSchedule = new WorkSchedule(schedule.technicianId(), newStartTime, newExpectedDuration);
        if (newSchedule.equals(schedule)) {
            throw new UnchangedScheduleException();
        }
        changeAssignment(newSchedule, changedAt, changedBy, AssignmentEndReason.RESCHEDULED);
    }

    /** 배정을 풀어 대기함으로 돌린다. 수락된 배정도 결과는 그대로 두고 해제 시각·처리자를 종료로 남긴다. */
    public void unassign(Instant unassignedAt, MembershipId unassignedBy) {
        if (status != WorkStatus.PENDING_ACCEPTANCE && status != WorkStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot unassign when status is " + status);
        }
        WorkStatus nextStatus = status.transitionTo(WorkStatus.REGISTERED);
        AssignmentEnding ending = new AssignmentEnding(unassignedAt, unassignedBy, AssignmentEndReason.UNASSIGNED);
        requireNotBeforeLatestCorrection(ending.endedAt(), "endedAt");
        latestAssignment().end(ending);
        schedule = null;
        status = nextStatus;
    }

    /**
     * 작업명·작업 유형·고객정보·결제정보 네 항목을 주어진 값으로 한꺼번에 교체한다. null로 준 선택 항목은 비운다(부분 수정이 아니다). 완료·취소된 작업은 바꿀 수
     * 없고, 상태·배정은 그대로 둔다. 서비스의 공통 오류 순서(입력 → 상태)에 맞춰 작업명을 상태보다 먼저 검증한다.
     */
    public void changeDetails(
            String newName, WorkTypeId newWorkType, CustomerInfo newCustomerInfo, PaymentInfo newPaymentInfo) {
        requireName(newName);
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot change details when status is " + status);
        }
        name = newName;
        workType = newWorkType;
        customerInfo = orEmpty(newCustomerInfo);
        paymentInfo = orEmpty(newPaymentInfo);
    }

    /** 수락된 작업을 시작해 작업중으로 바꾸고 시작 시각을 남긴다. 시작 시각은 수락 시각보다 앞설 수 없다. 예정 시작시각과는 관계없이 시작할 수 있다. */
    public void start(Instant startedAt) {
        requireStatus(WorkStatus.ACCEPTED, "start");
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        Instant storedStartedAt = truncateToMicros(startedAt);
        if (storedStartedAt.isBefore(latestAssignment().lastMoment())) {
            throw new IllegalArgumentException("startedAt must not be before the assignment was accepted");
        }
        requireNotBeforeLatestCorrection(storedStartedAt, "startedAt");
        WorkStatus nextStatus = status.transitionTo(WorkStatus.IN_PROGRESS);
        this.startedAt = storedStartedAt;
        status = nextStatus;
    }

    /** 작업중인 작업에 완료보고를 붙여 완료로 바꾸고 완료 시각을 남긴다. 보고 저장과 완료 전환은 함께 일어난다. 완료 시각은 시작 시각보다 앞설 수 없다. */
    public void submitCompletionReport(CompletionReport report, Instant completedAt) {
        requireStatus(WorkStatus.IN_PROGRESS, "submit completion report");
        if (report == null) {
            throw new IllegalArgumentException("completionReport must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        Instant storedCompletedAt = truncateToMicros(completedAt);
        if (storedCompletedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        requireNotBeforeLatestCorrection(storedCompletedAt, "completedAt");
        WorkStatus nextStatus = status.transitionTo(WorkStatus.COMPLETED);
        completionReport = report;
        this.completedAt = storedCompletedAt;
        status = nextStatus;
    }

    /**
     * 완료 전 작업을 취소하고 취소 시각·처리자·사유를 작업에 남긴다. 현재 배정이 있으면(수락대기·수락됨·작업중) 같은 시각·처리자를 그 배정의 종료로도 남겨,
     * 배정이 왜 끝났는지 이력에서 알 수 있게 한다. 작업중이던 작업은 시작 시각을 그대로 둔다. 취소 시각은 배정 이력의 마지막 시각, 시작 시각과 마지막
     * 상태 정정 시각보다 앞설 수 없다. 입력(사유 등)을 상태보다 먼저 검증한다.
     */
    public void cancel(Instant cancelledAt, MembershipId cancelledBy, String reason) {
        Cancellation newCancellation = new Cancellation(cancelledAt, cancelledBy, reason);
        WorkStatus cancelled = status.transitionTo(WorkStatus.CANCELLED);
        requireNotBeforeStart(newCancellation.cancelledAt(), "cancelledAt");
        // 현재 배정이 있어도 없어도 같은 이름(cancelledAt)으로 알리도록, 배정 종료의 하한보다 먼저 확인한다.
        requireNotBeforeLatestAssignment(newCancellation.cancelledAt(), "cancelledAt");
        requireNotBeforeLatestCorrection(newCancellation.cancelledAt(), "cancelledAt");
        if (status != WorkStatus.REGISTERED) {
            latestAssignment()
                    .end(new AssignmentEnding(
                            newCancellation.cancelledAt(),
                            newCancellation.cancelledBy(),
                            AssignmentEndReason.CANCELLED));
        }
        cancellation = newCancellation;
        status = cancelled;
    }

    /**
     * 총관리자가 작업 상태를 한 단계 되돌린다(완료 → 작업중, 작업중 → 수락됨, 취소 → 대기함). 도착 상태에 맞지 않는 데이터(완료보고·완료 시각, 시작 시각,
     * 취소 기록·일정)는 작업에서 치워 정정 기록에 보관한다. 배정 이력은 그대로 둔다. 입력 → 상태 → 시각 순서로 확인하고, 모두 통과한 뒤에만 바꾼다. 정정
     * 시각은 작업에 남은 가장 늦은 기록보다 앞설 수 없다.
     */
    public void correctStatus(WorkStatus target, Instant correctedAt, MembershipId correctedBy, String reason) {
        StatusCorrection.requireValidInput(target, correctedAt, correctedBy, reason);
        if (!status.canBeCorrectedTo(target)) {
            throw new IllegalStateException("Cannot correct status from " + status + " to " + target);
        }
        Instant at = truncateToMicros(correctedAt);
        if (at.isBefore(latestRecordedMoment())) {
            throw new IllegalArgumentException("correctedAt must not be before the latest record");
        }
        boolean fromCompleted = status == WorkStatus.COMPLETED;
        boolean fromCancelled = status == WorkStatus.CANCELLED;
        Integer restoredAssignmentNumber =
                fromCancelled && currentCancelledAssignment().isPresent() ? assignmentHistory.size() : null;
        StatusCorrection correction = new StatusCorrection(
                status,
                target,
                at,
                correctedBy,
                reason,
                fromCompleted ? completionReport : null,
                fromCompleted ? completedAt : null,
                fromCompleted ? null : startedAt,
                fromCancelled ? cancellation : null,
                restoredAssignmentNumber);
        if (fromCompleted) {
            completionReport = null;
            completedAt = null;
        } else {
            startedAt = null;
        }
        if (fromCancelled) {
            cancellation = null;
            schedule = null;
        }
        statusCorrections.add(correction);
        status = target;
    }

    /** 작업에 남은 가장 늦은 기록 시각(배정 이력의 마지막 시각, 시작·완료·취소·정정 시각). 없으면 {@link Instant#MIN}. */
    private Instant latestRecordedMoment() {
        return Stream.of(
                        assignmentHistory.isEmpty()
                                ? null
                                : assignmentHistory.getLast().lastMoment(),
                        statusCorrections.isEmpty()
                                ? null
                                : statusCorrections.getLast().correctedAt(),
                        startedAt,
                        completedAt,
                        cancellation == null ? null : cancellation.cancelledAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
    }

    /** 정정 뒤에 기록하는 시각(배정·시작·완료·해제·취소)이 마지막 정정 시각보다 앞서지 않게 한다. field는 오류 메시지에 쓸 시각 이름이다. */
    private void requireNotBeforeLatestCorrection(Instant at, String field) {
        if (!statusCorrections.isEmpty()
                && at.isBefore(statusCorrections.getLast().correctedAt())) {
            throw new IllegalArgumentException(field + " must not be before the latest status correction");
        }
    }

    private void requireChangeableAssignment(String action) {
        if (status != WorkStatus.PENDING_ACCEPTANCE && status != WorkStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot " + action + " when status is " + status);
        }
    }

    /** 현재 배정을 끝내고(응답 전이면 회수, 수락됐으면 결과를 둔 채 종료만) 새 배정을 추가해 다시 수락받는다. */
    private void changeAssignment(
            WorkSchedule newSchedule, Instant changedAt, MembershipId changedBy, AssignmentEndReason reason) {
        // 새 배정 이력과 종료 기록을 먼저 만들어 입력·시각을 검증한 뒤 반영해, 예외가 나도 작업이 부분적으로 바뀌지 않게 한다.
        AssignmentHistory newAssignment = new AssignmentHistory(newSchedule, changedAt, changedBy);
        AssignmentEnding ending = new AssignmentEnding(changedAt, changedBy, reason);
        requireNotBeforeLatestAssignment(changedAt, "assignedAt");
        requireNotBeforeLatestCorrection(changedAt, "assignedAt");
        WorkStatus nextStatus =
                status == WorkStatus.ACCEPTED ? status.transitionTo(WorkStatus.PENDING_ACCEPTANCE) : status;
        latestAssignment().end(ending);
        schedule = newSchedule;
        assignmentHistory.add(newAssignment);
        status = nextStatus;
    }

    /**
     * 새 배정·취소 시각이 최신 배정 이력의 마지막 시각(종료·응답·배정 시각 중 가장 늦은 것)보다 이르지 않은지 확인해 이력의 시간 순서를 지킨다. field는
     * 오류 메시지에 쓸 시각 이름이다.
     */
    private void requireNotBeforeLatestAssignment(Instant at, String field) {
        if (!assignmentHistory.isEmpty()
                && at.isBefore(assignmentHistory.getLast().lastMoment())) {
            throw new IllegalArgumentException(field + " must not be before the latest assignment");
        }
    }

    /** 작업중이던 작업을 끝내는 시각이 시작 시각보다 이르지 않은지 확인한다. field는 오류 메시지에 쓸 시각 이름이다. */
    private void requireNotBeforeStart(Instant at, String field) {
        if (startedAt != null && at.isBefore(startedAt)) {
            throw new IllegalArgumentException(field + " must not be before startedAt");
        }
    }

    /** 저장값 복원 시 상태와 일정·배정 이력·완료보고·시작·완료 시각·취소 기록·상태 정정 기록이 서로 맞는지 검증한다. */
    private void requireConsistentState() {
        for (int i = 0; i < assignmentHistory.size() - 1; i++) {
            AssignmentHistory previous = assignmentHistory.get(i);
            if (previous.result() == AssignmentResult.PENDING) {
                throw new IllegalArgumentException("Only the latest assignment history can be PENDING");
            }
            if (!previous.isOver()) {
                throw new IllegalArgumentException("Only the latest assignment history can be current");
            }
            AssignmentHistory next = assignmentHistory.get(i + 1);
            int previousNumber = i + 1;
            if (previous.ending()
                    .filter(ending -> ending.reason() == AssignmentEndReason.CANCELLED)
                    .filter(ending -> !restoredAssignmentNumbers().contains(previousNumber))
                    .isPresent()) {
                throw new IllegalArgumentException("Only the latest assignment history can end by cancellation");
            }
            if (next.assignedAt().isBefore(previous.lastMoment())) {
                throw new IllegalArgumentException("assignment histories must be in chronological order");
            }
            previous.ending().ifPresent(ending -> requireFollowedByChange(ending, previous, next));
        }
        AssignmentHistory latest = assignmentHistory.isEmpty() ? null : assignmentHistory.getLast();
        if (status == WorkStatus.REGISTERED && schedule != null) {
            throw new IllegalArgumentException("REGISTERED work must not have a schedule");
        }
        if (status == WorkStatus.REGISTERED) {
            // 대기함으로 되돌린 취소 종료만 허용한다(되돌리지 않은 취소 종료가 남아 있으면 취소 상태여야 한다).
            if (currentCancelledAssignment().isPresent()) {
                throw new IllegalArgumentException("REGISTERED work must not have an assignment ended by CANCELLED");
            }
            requireOverAssignment(latest, AssignmentEndReason.UNASSIGNED, AssignmentEndReason.CANCELLED);
        } else if (status == WorkStatus.CANCELLED) {
            requireOverAssignment(latest, AssignmentEndReason.UNASSIGNED, AssignmentEndReason.CANCELLED);
            requireScheduleOfCancelledAssignment(latest);
        } else if (status == WorkStatus.PENDING_ACCEPTANCE) {
            requireAssignedSchedule(latest, AssignmentResult.PENDING);
        } else {
            requireAssignedSchedule(latest, AssignmentResult.ACCEPTED);
        }
        if (status == WorkStatus.COMPLETED && completionReport == null) {
            throw new IllegalArgumentException("COMPLETED work must have a completion report");
        }
        if (status != WorkStatus.COMPLETED && completionReport != null) {
            throw new IllegalArgumentException(status + " work must not have a completion report");
        }
        requireConsistentProgressTimes(latest);
        requireConsistentCancellation(latest);
        requireConsistentCorrections();
    }

    /** 대기함으로 되돌린 정정이 가리키는 배정 순번들. */
    private Set<Integer> restoredAssignmentNumbers() {
        return statusCorrections.stream()
                .map(StatusCorrection::restoredAssignmentNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 최신 배정의 취소 종료 중 아직 대기함으로 되돌리지 않은 것. 되돌린 취소 종료는 배정이 왜 끝났는지의 흔적으로만 남는다. */
    private Optional<AssignmentEnding> currentCancelledAssignment() {
        if (assignmentHistory.isEmpty() || restoredAssignmentNumbers().contains(assignmentHistory.size())) {
            return Optional.empty();
        }
        return assignmentHistory.getLast().ending().filter(ending -> ending.reason() == AssignmentEndReason.CANCELLED);
    }

    /**
     * 정정 기록을 작업의 나머지 기록과 맞춰 본다. 정정은 시간 순서로 쌓이고, 대기함으로 되돌린 정정이 가리키는 배정은 그 정정의 취소 기록과 같은 시각·처리자의
     * 취소로 끝났으며, 한 배정의 취소는 한 번만 되돌린다. 되돌린 뒤 이어지는 배정과 정정 뒤에 남은 시작·완료·취소 시각은 정정보다 앞서지 않는다.
     *
     * <p>정정 기록은 정정 당시 배정 이력의 위치를 담지 않으므로, 배정 이력과의 전후는 되돌린 취소 배정 바로 뒤의 배정만 비교한다. 대기함 취소를 되돌린 정정이나
     * 작업중 → 수락됨 정정과 그 앞뒤 배정 사이의 시간 역전은 여기서 거르지 못한다. 도메인 메서드로는 이런 저장값을 만들 수 없으므로, 저장소가 기록 순서를 그대로
     * 보존하는 것에 기댄다.
     */
    private void requireConsistentCorrections() {
        Set<Integer> restored = new HashSet<>();
        for (int i = 0; i < statusCorrections.size(); i++) {
            StatusCorrection correction = statusCorrections.get(i);
            if (i > 0
                    && correction
                            .correctedAt()
                            .isBefore(statusCorrections.get(i - 1).correctedAt())) {
                throw new IllegalArgumentException("status corrections must be in chronological order");
            }
            Integer number = correction.restoredAssignmentNumber();
            if (number == null) {
                continue;
            }
            if (!restored.add(number) || number > assignmentHistory.size()) {
                throw new IllegalArgumentException("status correction must restore an existing assignment once");
            }
            AssignmentHistory history = assignmentHistory.get(number - 1);
            Cancellation retired = correction.retiredCancellation();
            boolean matches = history.ending()
                    .filter(ending -> ending.reason() == AssignmentEndReason.CANCELLED)
                    .filter(ending -> ending.endedAt().equals(retired.cancelledAt()))
                    .filter(ending -> ending.endedBy().equals(retired.cancelledBy()))
                    .isPresent();
            if (!matches) {
                throw new IllegalArgumentException("status correction must match the cancelled assignment it restores");
            }
            if (number < assignmentHistory.size()
                    && assignmentHistory.get(number).assignedAt().isBefore(correction.correctedAt())) {
                throw new IllegalArgumentException("assignment after a restoration must not precede the correction");
            }
        }
        if (statusCorrections.isEmpty()) {
            return;
        }
        Instant lastCorrection = statusCorrections.getLast().correctedAt();
        if (completedAt != null && completedAt.isBefore(lastCorrection)) {
            throw new IllegalArgumentException("completedAt must not be before the latest status correction");
        }
        if (cancellation != null && cancellation.cancelledAt().isBefore(lastCorrection)) {
            throw new IllegalArgumentException("cancelledAt must not be before the latest status correction");
        }
        boolean startedAfterReset = statusCorrections.stream()
                .filter(correction -> correction.to() != WorkStatus.IN_PROGRESS)
                .allMatch(correction -> startedAt == null || !startedAt.isBefore(correction.correctedAt()));
        if (!startedAfterReset) {
            throw new IllegalArgumentException("startedAt must not be before the status correction that cleared it");
        }
    }

    private void requireConsistentCancellation(AssignmentHistory latest) {
        if (status == WorkStatus.CANCELLED && cancellation == null) {
            throw new IllegalArgumentException("CANCELLED work must have a cancellation");
        }
        if (status != WorkStatus.CANCELLED && cancellation != null) {
            throw new IllegalArgumentException(status + " work must not have a cancellation");
        }
        if (cancellation == null || latest == null) {
            return;
        }
        Optional<AssignmentEnding> cancelledAssignment = currentCancelledAssignment();
        if (cancelledAssignment.isPresent()) {
            AssignmentEnding ending = cancelledAssignment.get();
            if (!ending.endedAt().equals(cancellation.cancelledAt())
                    || !ending.endedBy().equals(cancellation.cancelledBy())) {
                throw new IllegalArgumentException("cancellation must match the ending of the cancelled assignment");
            }
            return;
        }
        requireNotBeforeLatestAssignment(cancellation.cancelledAt(), "cancelledAt");
    }

    /**
     * 시작 시각은 작업중·완료된 작업에 반드시 있고, 수락된 뒤 취소된 작업에는 작업중에 취소됐을 때만 있다. 완료 시각은 완료된 작업에만 있다. 시간 순서는 수락 → 시작
     * → 완료·취소다.
     */
    private void requireConsistentProgressTimes(AssignmentHistory latest) {
        boolean started = status == WorkStatus.IN_PROGRESS || status == WorkStatus.COMPLETED;
        boolean cancelledAfterAcceptance = status == WorkStatus.CANCELLED
                && latest != null
                && latest.result() == AssignmentResult.ACCEPTED
                && currentCancelledAssignment().isPresent();
        if (started && startedAt == null) {
            throw new IllegalArgumentException(status + " work must have startedAt");
        }
        if (!started && !cancelledAfterAcceptance && startedAt != null) {
            throw new IllegalArgumentException(status + " work must not have startedAt");
        }
        if (status == WorkStatus.COMPLETED && completedAt == null) {
            throw new IllegalArgumentException("COMPLETED work must have completedAt");
        }
        if (status != WorkStatus.COMPLETED && completedAt != null) {
            throw new IllegalArgumentException(status + " work must not have completedAt");
        }
        if (startedAt == null) {
            return;
        }
        if (startedAt.isBefore(latest.decidedAt().orElseThrow())) {
            throw new IllegalArgumentException("startedAt must not be before the assignment was accepted");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        latest.ending().ifPresent(ending -> requireNotBeforeStart(ending.endedAt(), "endedAt"));
    }

    private void requireAssignedSchedule(AssignmentHistory latest, AssignmentResult expectedResult) {
        if (schedule == null) {
            throw new IllegalArgumentException(status + " work must have a schedule");
        }
        if (latest == null
                || latest.result() != expectedResult
                || latest.isOver()
                || !latest.schedule().equals(schedule)) {
            throw new IllegalArgumentException(
                    status + " work requires the latest assignment to be " + expectedResult + " for its schedule");
        }
    }

    /**
     * 재배정·일정 변경으로 끝난 배정 바로 다음에는 같은 시각·같은 처리자가 만든 새 배정이 온다. 재배정은 다른 기사, 일정 변경은 같은 기사의 다른 일정이다. 해제로 끝난 배정 뒤의
     * 배정은 대기함에서 새로 한 배정이라 시각 순서만 지키면 된다.
     */
    private static void requireFollowedByChange(
            AssignmentEnding ending, AssignmentHistory previous, AssignmentHistory next) {
        if (ending.reason() != AssignmentEndReason.REASSIGNED && ending.reason() != AssignmentEndReason.RESCHEDULED) {
            return;
        }
        if (!next.assignedAt().equals(ending.endedAt()) || !next.assignedBy().equals(ending.endedBy())) {
            throw new IllegalArgumentException(
                    "assignment ended by " + ending.reason() + " must be followed by the assignment it made");
        }
        boolean sameTechnician =
                next.schedule().technicianId().equals(previous.schedule().technicianId());
        if (sameTechnician != (ending.reason() == AssignmentEndReason.RESCHEDULED)) {
            throw new IllegalArgumentException(
                    "assignment ended by " + ending.reason() + " must be followed by a matching technician");
        }
        if (ending.reason() == AssignmentEndReason.RESCHEDULED
                && next.schedule().equals(previous.schedule())) {
            throw new IllegalArgumentException(
                    "assignment ended by RESCHEDULED must be followed by a different schedule");
        }
    }

    /** 배정이 있던 채로 취소된 작업만 그 배정의 일정을 남긴다. 대기함에서 취소된 작업은 일정이 없다. */
    private void requireScheduleOfCancelledAssignment(AssignmentHistory latest) {
        boolean cancelledWhileAssigned = currentCancelledAssignment().isPresent();
        if (cancelledWhileAssigned && !latest.schedule().equals(schedule)) {
            throw new IllegalArgumentException("CANCELLED work must keep the schedule of the cancelled assignment");
        }
        if (!cancelledWhileAssigned && schedule != null) {
            throw new IllegalArgumentException("CANCELLED work from the backlog must not have a schedule");
        }
    }

    /** 현재 배정이 없는 상태면 최신 이력도 끝나 있어야 하고(거절 또는 허용된 방식의 종료), 대기 중일 수 없다. */
    private void requireOverAssignment(AssignmentHistory latest, AssignmentEndReason... allowedReasons) {
        if (latest == null) {
            return;
        }
        if (latest.result() == AssignmentResult.PENDING) {
            throw new IllegalArgumentException(status + " work must not have a PENDING assignment");
        }
        if (!latest.isOver()) {
            throw new IllegalArgumentException(status + " work must not have a current assignment");
        }
        latest.ending().ifPresent(ending -> {
            if (!List.of(allowedReasons).contains(ending.reason())) {
                throw new IllegalArgumentException(
                        status + " work must not have an assignment ended by " + ending.reason());
            }
        });
    }

    private void requireStatus(WorkStatus requiredStatus, String action) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Cannot " + action + " when status is " + status);
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
    }

    private static Instant truncateToMicros(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static CustomerInfo orEmpty(CustomerInfo customerInfo) {
        return customerInfo == null ? new CustomerInfo(null, null, null) : customerInfo;
    }

    private static PaymentInfo orEmpty(PaymentInfo paymentInfo) {
        return paymentInfo == null ? new PaymentInfo(null, null) : paymentInfo;
    }

    private static void requireSchedule(WorkSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
    }

    private AssignmentHistory latestAssignment() {
        if (assignmentHistory.isEmpty()) {
            throw new IllegalStateException("No assignment history");
        }
        return assignmentHistory.getLast();
    }
}
