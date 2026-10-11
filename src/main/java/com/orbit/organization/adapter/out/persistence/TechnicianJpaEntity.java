package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 기사 계약. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 계약은 1행이다. 기사 계약 도메인(Technician)과 생성·역할 변경은 참여 요청
 * 승인·참여 인원 작업이 추가하고, 지금은 기사 유형 삭제 때 유형 해제만 한다. 그래서 상태는 도메인 타입 없이 저장 값 그대로 둔다.
 */
@Entity
@Table(name = "technician", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "member_id"}))
class TechnicianJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    /** 기사 유형. NULL은 "유형 없음"이다. */
    @Column(name = "company_technician_category_id")
    private Long technicianCategoryId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TechnicianJpaEntity() {}
}
