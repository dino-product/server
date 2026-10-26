package com.orbit.schedule.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 사무실이 담당 기사에게 완료보고를 요청한 기록 한 건. 요청 순서는 {@code id} 순이고 추가만 한다. {@code requested_by}는 요청한 관리자의 조직 소속
 * 식별자(organization 모듈의 소속 테이블을 논리 참조)다. ERD에 없는 테이블이라 ERD에 추가한다.
 */
@Entity
@Table(
        name = "work_completion_report_requests",
        indexes = @Index(name = "ix_work_completion_report_requests_work", columnList = "work_id"))
class WorkCompletionReportRequestJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkJpaEntity work;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    protected WorkCompletionReportRequestJpaEntity() {}

    WorkCompletionReportRequestJpaEntity(WorkJpaEntity work, Instant requestedAt, Long requestedBy) {
        this.work = work;
        this.requestedAt = requestedAt;
        this.requestedBy = requestedBy;
    }

    Long id() {
        return id;
    }

    Instant requestedAt() {
        return requestedAt;
    }

    Long requestedBy() {
        return requestedBy;
    }
}
