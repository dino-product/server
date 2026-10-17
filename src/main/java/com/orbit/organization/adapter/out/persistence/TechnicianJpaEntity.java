package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 기사 계약. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 기사 계약은 1행이며 역할 변경으로 다시 활성이 되면 이 행을 재사용한다.
 * 지금은 활성 구성원 조회({@link ActiveMemberQueryAdapter})가 읽고 기사 유형 삭제가 유형만 벌크 갱신({@link SpringDataTechnicianRepository})하므로
 * 도메인 변환이 없다. 계약 생성(참여 요청 승인)과 함께 도메인·저장을 추가한다.
 */
@Entity
@Table(name = "technician", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "member_id"}))
class TechnicianJpaEntity extends BaseTimeEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TechnicianStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected TechnicianJpaEntity() {}
}
