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

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/**
 * 기사 계약. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 기사 계약은 1행이며 역할 변경으로 다시 활성이 되면 이 행을 재사용한다.
 * 기사 유형 컬럼은 유형 관리(HM-291)와 함께 추가한다.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TechnicianStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TechnicianJpaEntity() {}

    Technician toDomain() {
        return Technician.reconstitute(
                new TechnicianId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                status,
                createdAt,
                statusChangedAt);
    }
}
