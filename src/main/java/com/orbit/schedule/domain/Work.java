package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 작업 등록부터 배정, 수행, 완료까지의 생애주기를 관리하는 애그리게잇 루트. */
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
            CompletionReport completionReport) {
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
                null);
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
            CompletionReport completionReport) {
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
                completionReport);
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

    /** 대기함 작업을 기사·일정에 배정하고 수락을 기다린다. 배정한 관리자(조직 소속)를 이력에 남긴다. */
    public void assign(WorkSchedule newSchedule, Instant assignedAt, MembershipId assignedBy) {
        requireStatus(WorkStatus.REGISTERED, "assign");
        requireSchedule(newSchedule);
        // 검증을 모두 마친 뒤 반영해, 예외가 나도 작업이 부분적으로 바뀌지 않게 한다.
        AssignmentHistory newAssignment = new AssignmentHistory(newSchedule, assignedAt, assignedBy);
        requireNotBeforeLatestAssignment(assignedAt);
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
        latestAssignment().end(new AssignmentEnding(unassignedAt, unassignedBy, AssignmentEndReason.UNASSIGNED));
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

    public void start() {
        requireStatus(WorkStatus.ACCEPTED, "start");
        status = status.transitionTo(WorkStatus.IN_PROGRESS);
    }

    public void submitCompletionReport(CompletionReport report) {
        requireStatus(WorkStatus.IN_PROGRESS, "submit completion report");
        if (report == null) {
            throw new IllegalArgumentException("completionReport must not be null");
        }
        WorkStatus nextStatus = status.transitionTo(WorkStatus.COMPLETED);
        completionReport = report;
        status = nextStatus;
    }

    /** 완료 전 작업을 취소한다. 현재 배정이 있으면(수락대기·수락됨·작업중) 취소 시각·처리자를 그 배정의 종료로 남긴다. */
    public void cancel(Instant cancelledAt, MembershipId cancelledBy) {
        // 대기함 작업은 끝낼 배정이 없지만, 어느 상태에서든 같은 입력 규칙을 적용한다.
        AssignmentEnding ending = new AssignmentEnding(cancelledAt, cancelledBy, AssignmentEndReason.CANCELLED);
        WorkStatus cancelled = status.transitionTo(WorkStatus.CANCELLED);
        if (status != WorkStatus.REGISTERED) {
            latestAssignment().end(ending);
        }
        status = cancelled;
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
        requireNotBeforeLatestAssignment(changedAt);
        WorkStatus nextStatus =
                status == WorkStatus.ACCEPTED ? status.transitionTo(WorkStatus.PENDING_ACCEPTANCE) : status;
        latestAssignment().end(ending);
        schedule = newSchedule;
        assignmentHistory.add(newAssignment);
        status = nextStatus;
    }

    /** 새 배정 시각이 최신 배정 이력의 마지막 시각(종료·응답·배정 시각 중 가장 늦은 것)보다 이르지 않은지 확인해 이력의 시간 순서를 지킨다. */
    private void requireNotBeforeLatestAssignment(Instant at) {
        if (assignmentHistory.isEmpty()) {
            return;
        }
        if (at.isBefore(assignmentHistory.getLast().lastMoment())) {
            throw new IllegalArgumentException("assignedAt must not be before the latest assignment");
        }
    }

    /** 저장값 복원 시 상태와 일정·배정 이력·완료보고가 서로 맞는지 검증한다. */
    private void requireConsistentState() {
        for (int i = 0; i < assignmentHistory.size() - 1; i++) {
            AssignmentHistory previous = assignmentHistory.get(i);
            if (previous.result() == AssignmentResult.PENDING) {
                throw new IllegalArgumentException("Only the latest assignment history can be PENDING");
            }
            if (!previous.isOver()) {
                throw new IllegalArgumentException("Only the latest assignment history can be current");
            }
            if (previous.ending()
                    .map(ending -> ending.reason() == AssignmentEndReason.CANCELLED)
                    .orElse(false)) {
                throw new IllegalArgumentException("Only the latest assignment history can end by cancellation");
            }
            AssignmentHistory next = assignmentHistory.get(i + 1);
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
            requireOverAssignment(latest, AssignmentEndReason.UNASSIGNED);
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
        boolean cancelledWhileAssigned = latest != null
                && latest.ending()
                        .map(ending -> ending.reason() == AssignmentEndReason.CANCELLED)
                        .orElse(false);
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
