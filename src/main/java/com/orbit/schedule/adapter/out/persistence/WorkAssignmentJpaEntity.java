package com.orbit.schedule.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 작업의 배정 이력 한 건({@code AssignmentHistory}). 테이블 구조는 팀 ERD의 {@code Work_Assignments}를 따른다.
 *
 * <p>이력은 추가만 되고 배정 순번은 부모 컬렉션 안의 {@code id} 순서다(ERD에 순번 컬럼이 없음). 배정한·종료한 관리자와 기사 계약 식별자는 organization 모듈
 * 소유라 FK 없는 {@code Long} 컬럼이다. 결과·응답 시각·거절·종료 기록은 Domain이 바꾼 값을 저장 때 덮어쓴다.
 */
@Entity
@Table(name = "work_assignments", indexes = @Index(name = "ix_work_assignments_work", columnList = "work_id"))
class WorkAssignmentJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkJpaEntity work;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "expected_duration", nullable = false)
    private Duration expectedDuration;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AssignmentResult result;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 20)
    private RejectionReason rejectionReason;

    @Column(name = "rejection_note", length = Rejection.MAX_NOTE_LENGTH)
    private String rejectionNote;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by")
    private Long endedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private AssignmentEndReason endReason;

    protected WorkAssignmentJpaEntity() {}

    WorkAssignmentJpaEntity(
            WorkJpaEntity work,
            Long technicianId,
            Long assignedBy,
            Instant scheduledStartAt,
            Duration expectedDuration,
            Instant assignedAt,
            AssignmentResult result) {
        this.work = work;
        this.technicianId = technicianId;
        this.assignedBy = assignedBy;
        this.scheduledStartAt = scheduledStartAt;
        this.expectedDuration = expectedDuration;
        this.assignedAt = assignedAt;
        this.result = result;
    }

    Long id() {
        return id;
    }

    Long technicianId() {
        return technicianId;
    }

    Long assignedBy() {
        return assignedBy;
    }

    Instant scheduledStartAt() {
        return scheduledStartAt;
    }

    Duration expectedDuration() {
        return expectedDuration;
    }

    Instant assignedAt() {
        return assignedAt;
    }

    AssignmentResult result() {
        return result;
    }

    Instant decidedAt() {
        return decidedAt;
    }

    RejectionReason rejectionReason() {
        return rejectionReason;
    }

    String rejectionNote() {
        return rejectionNote;
    }

    Instant endedAt() {
        return endedAt;
    }

    Long endedBy() {
        return endedBy;
    }

    AssignmentEndReason endReason() {
        return endReason;
    }

    /** 기사 응답(수락·거절) 결과. 거절이 아니면 사유·메모는 null이다. */
    void updateDecision(
            AssignmentResult newResult,
            Instant newDecidedAt,
            RejectionReason newRejectionReason,
            String newRejectionNote) {
        this.result = newResult;
        this.decidedAt = newDecidedAt;
        this.rejectionReason = newRejectionReason;
        this.rejectionNote = newRejectionNote;
    }

    /** 관리자 조치로 배정이 끝난 기록. 끝나지 않았으면 null 셋. */
    void updateEnding(Instant newEndedAt, Long newEndedBy, AssignmentEndReason newEndReason) {
        this.endedAt = newEndedAt;
        this.endedBy = newEndedBy;
        this.endReason = newEndReason;
    }
}
