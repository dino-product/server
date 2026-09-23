package com.orbit.schedule.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 작업 등록부터 배정, 수행, 완료까지의 생애주기를 관리하는 애그리게잇 루트. */
public final class Work {

    private final WorkId id;
    private final String name;
    private final MembershipId registrarId;
    private final WorkTypeId workType;
    private WorkSchedule schedule;
    private final CustomerInfo customerInfo;
    private final PaymentInfo paymentInfo;
    private WorkStatus status;
    private final List<AssignmentHistory> assignmentHistory;
    private CompletionReport completionReport;

    private Work(
            WorkId id,
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            WorkSchedule schedule,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo,
            WorkStatus status,
            List<AssignmentHistory> assignmentHistory,
            CompletionReport completionReport) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (registrarId == null) {
            throw new IllegalArgumentException("registrarId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.id = id;
        this.name = name;
        this.registrarId = registrarId;
        this.workType = workType;
        this.schedule = schedule;
        this.customerInfo = customerInfo == null ? new CustomerInfo(null, null, null) : customerInfo;
        this.paymentInfo = paymentInfo == null ? new PaymentInfo(null, null) : paymentInfo;
        this.status = status;
        this.assignmentHistory = assignmentHistory == null ? new ArrayList<>() : new ArrayList<>(assignmentHistory);
        this.completionReport = completionReport;
    }

    public static Work register(
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo) {
        return new Work(
                null,
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
            String name,
            MembershipId registrarId,
            WorkTypeId workType,
            WorkSchedule schedule,
            CustomerInfo customerInfo,
            PaymentInfo paymentInfo,
            WorkStatus status,
            List<AssignmentHistory> assignmentHistory,
            CompletionReport completionReport) {
        return new Work(
                Objects.requireNonNull(id, "id must not be null"),
                name,
                registrarId,
                workType,
                schedule,
                customerInfo,
                paymentInfo,
                status,
                assignmentHistory,
                completionReport);
    }

    public Optional<WorkId> id() {
        return Optional.ofNullable(id);
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
        schedule = newSchedule;
        assignmentHistory.add(new AssignmentHistory(newSchedule, assignedAt));
        status = status.transitionTo(WorkStatus.PENDING_ACCEPTANCE);
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

    public void reassign(WorkSchedule newSchedule, Instant changedAt) {
        changeAssignment(newSchedule, changedAt, "reassign");
    }

    public void reschedule(WorkSchedule newSchedule, Instant changedAt) {
        changeAssignment(newSchedule, changedAt, "reschedule");
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

    public void cancel() {
        status = status.transitionTo(WorkStatus.CANCELLED);
    }

    private void changeAssignment(WorkSchedule newSchedule, Instant changedAt, String action) {
        requireStatus(WorkStatus.PENDING_ACCEPTANCE, action);
        requireSchedule(newSchedule);
        latestAssignment().reassign(changedAt);
        schedule = newSchedule;
        assignmentHistory.add(new AssignmentHistory(newSchedule, changedAt));
    }

    private void requireStatus(WorkStatus requiredStatus, String action) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Cannot " + action + " when status is " + status);
        }
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
