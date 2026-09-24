package com.orbit.schedule.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 작업 등록부터 배정, 수행, 완료까지의 생애주기를 관리하는 애그리게잇 루트. */
public final class Work {

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

    public void assign(WorkSchedule newSchedule, Instant assignedAt) {
        requireStatus(WorkStatus.REGISTERED, "assign");
        requireSchedule(newSchedule);
        // 검증을 모두 마친 뒤 반영해, 예외가 나도 작업이 부분적으로 바뀌지 않게 한다.
        AssignmentHistory newAssignment = new AssignmentHistory(newSchedule, assignedAt);
        requireNotBeforeLatestAssignment(assignedAt);
        WorkStatus nextStatus = status.transitionTo(WorkStatus.PENDING_ACCEPTANCE);
        schedule = newSchedule;
        assignmentHistory.add(newAssignment);
        status = nextStatus;
    }

    public void accept(Instant decidedAt) {
        requireStatus(WorkStatus.PENDING_ACCEPTANCE, "accept");
        latestAssignment().accept(decidedAt);
        status = status.transitionTo(WorkStatus.ACCEPTED);
    }

    public void reject(RejectionReason reason, Instant decidedAt) {
        requireStatus(WorkStatus.PENDING_ACCEPTANCE, "reject");
        latestAssignment().reject(reason, decidedAt);
        schedule = null;
        status = status.transitionTo(WorkStatus.REGISTERED);
    }

    /** 담당기사를 바꾼다. 수락 이후라도 새 기사에게 다시 수락받는다. */
    public void reassign(WorkSchedule newSchedule, Instant changedAt) {
        requireChangeableAssignment("reassign");
        requireSchedule(newSchedule);
        if (newSchedule.technicianId().equals(schedule.technicianId())) {
            throw new SameTechnicianException();
        }
        changeAssignment(newSchedule, changedAt);
    }

    /**
     * 담당기사는 그대로 두고 시작시각·예상소요시간을 바꾼다. 기사가 수락한 것은 원래 시간이므로 다시 수락받는다. 현재 기사로 일정을 만들어야 하므로 상태를 시간 입력보다
     * 먼저 확인한다. 입력을 상태보다 먼저 거르려면 호출자가 {@link WorkSchedule#requireValidTime}으로 미리 검증한다.
     */
    public void reschedule(Instant newStartTime, Duration newExpectedDuration, Instant changedAt) {
        requireChangeableAssignment("reschedule");
        WorkSchedule newSchedule = new WorkSchedule(schedule.technicianId(), newStartTime, newExpectedDuration);
        if (newSchedule.equals(schedule)) {
            throw new UnchangedScheduleException();
        }
        changeAssignment(newSchedule, changedAt);
    }

    public void unassign(Instant unassignedAt) {
        if (status != WorkStatus.PENDING_ACCEPTANCE && status != WorkStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot unassign when status is " + status);
        }
        if (status == WorkStatus.PENDING_ACCEPTANCE) {
            latestAssignment().reassign(unassignedAt);
        }
        // ACCEPTED 이력은 되돌릴 수 없는 과거 기록이므로 배정 해제 후에도 그대로 보존한다.
        schedule = null;
        status = status.transitionTo(WorkStatus.REGISTERED);
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
        completionReport = report;
        status = status.transitionTo(WorkStatus.COMPLETED);
    }

    /** 완료 전 작업을 취소한다. 응답 대기 중인 배정은 취소 시각으로 마감하고 수락된 이력은 그대로 둔다. */
    public void cancel(Instant cancelledAt) {
        WorkStatus cancelled = status.transitionTo(WorkStatus.CANCELLED);
        if (status == WorkStatus.PENDING_ACCEPTANCE) {
            latestAssignment().reassign(cancelledAt);
        }
        status = cancelled;
    }

    private void requireChangeableAssignment(String action) {
        if (status != WorkStatus.PENDING_ACCEPTANCE && status != WorkStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot " + action + " when status is " + status);
        }
    }

    /** 응답 대기 중인 배정은 마감하고, 이미 수락된 이력은 그대로 둔 채 새 배정을 추가해 다시 수락받는다. */
    private void changeAssignment(WorkSchedule newSchedule, Instant changedAt) {
        // 새 배정 이력을 먼저 만들어 시각을 검증한 뒤 반영해, 예외가 나도 작업이 부분적으로 바뀌지 않게 한다.
        AssignmentHistory newAssignment = new AssignmentHistory(newSchedule, changedAt);
        requireNotBeforeLatestAssignment(changedAt);
        WorkStatus nextStatus =
                status == WorkStatus.ACCEPTED ? status.transitionTo(WorkStatus.PENDING_ACCEPTANCE) : status;
        if (status == WorkStatus.PENDING_ACCEPTANCE) {
            latestAssignment().reassign(changedAt);
        }
        schedule = newSchedule;
        assignmentHistory.add(newAssignment);
        status = nextStatus;
    }

    /** 새 배정 시각이 최신 배정 이력의 마지막 시각(응답 시각, 없으면 배정 시각)보다 이르지 않은지 확인해 이력의 시간 순서를 지킨다. */
    private void requireNotBeforeLatestAssignment(Instant at) {
        if (assignmentHistory.isEmpty()) {
            return;
        }
        if (at.isBefore(lastMomentOf(assignmentHistory.getLast()))) {
            throw new IllegalArgumentException("assignedAt must not be before the latest assignment");
        }
    }

    /** 저장값 복원 시 상태와 일정·배정 이력·완료보고가 서로 맞는지 검증한다. */
    private void requireConsistentState() {
        for (int i = 0; i < assignmentHistory.size() - 1; i++) {
            if (assignmentHistory.get(i).result() == AssignmentResult.PENDING) {
                throw new IllegalArgumentException("Only the latest assignment history can be PENDING");
            }
            AssignmentHistory next = assignmentHistory.get(i + 1);
            if (next.assignedAt().isBefore(lastMomentOf(assignmentHistory.get(i)))) {
                throw new IllegalArgumentException("assignment histories must be in chronological order");
            }
        }
        AssignmentHistory latest = assignmentHistory.isEmpty() ? null : assignmentHistory.getLast();
        if (status == WorkStatus.REGISTERED && schedule != null) {
            throw new IllegalArgumentException("REGISTERED work must not have a schedule");
        }
        if (status == WorkStatus.REGISTERED || status == WorkStatus.CANCELLED) {
            requireNoPendingAssignment(latest);
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
                || !latest.schedule().equals(schedule)) {
            throw new IllegalArgumentException(
                    status + " work requires the latest assignment to be " + expectedResult + " for its schedule");
        }
    }

    private void requireNoPendingAssignment(AssignmentHistory latest) {
        if (latest != null && latest.result() == AssignmentResult.PENDING) {
            throw new IllegalArgumentException(status + " work must not have a PENDING assignment");
        }
    }

    /** 배정 이력의 마지막 시각. 응답·마감됐으면 응답 시각, 대기 중이면 배정 시각이다. */
    private static Instant lastMomentOf(AssignmentHistory history) {
        return history.decidedAt().orElse(history.assignedAt());
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
