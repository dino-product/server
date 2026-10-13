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
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 기사 계약. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 기사 계약은 1행이며 역할 변경으로 다시 활성이 되면 이 행을 재사용한다.
 * 새 계약은 참여 요청 승인이 만들고, 기사 유형은 유형 지정 작업이 도메인과 함께 연결한다(지금은 기사 유형 삭제가 벌크 갱신으로 해제만 한다).
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

    private TechnicianJpaEntity(Technician technician) {
        this.organizationId = technician.organizationId().value();
        this.accountId = technician.accountId().value();
        this.status = technician.status();
        this.statusChangedAt = technician.statusChangedAt();
    }

    static TechnicianJpaEntity from(Technician technician) {
        if (technician.id().isPresent()) {
            throw new IllegalArgumentException("only new technicians can be saved");
        }
        return new TechnicianJpaEntity(technician);
    }

    Technician toDomain() {
        return Technician.reconstitute(
                new TechnicianId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                status,
                statusChangedAt);
    }
}
