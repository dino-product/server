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
 * 기사 계약. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 기사 계약은 1행이며 다시 활성이 되면 이 행을 재사용한다. 새
 * 계약은 참여 요청 승인이 만들고, 기사 유형은 계약 도메인에 없어 지정·해제·벌크 해제를 저장 어댑터가 이 컬럼만 바꿔 처리한다. 활성
 * 구성원 조회({@link ActiveMemberQueryAdapter})·기사 계약 조회({@link TechnicianContractQueryAdapter})는 이 Entity 대신 SQL로 읽는다.
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

    /** 참여일. 승인 때 정하고 재활성화·역할 변경으로 바뀌지 않는다. 감사 컬럼 {@code created_at}과 따로 둔다. */
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected TechnicianJpaEntity() {}

    private TechnicianJpaEntity(Technician technician) {
        this.organizationId = technician.organizationId().value();
        this.accountId = technician.accountId().value();
        this.status = technician.status();
        this.joinedAt = technician.joinedAt();
        this.statusChangedAt = technician.statusChangedAt();
    }

    static TechnicianJpaEntity from(Technician technician) {
        if (technician.id().isPresent()) {
            throw new IllegalArgumentException("only new technicians can be saved");
        }
        return new TechnicianJpaEntity(technician);
    }

    void changeCategory(Long technicianCategoryId) {
        this.technicianCategoryId = technicianCategoryId;
    }

    void reactivate(Technician technician, boolean unassignCategory) {
        if (technician.status() != TechnicianStatus.ACTIVE) {
            throw new IllegalArgumentException("reactivated technician must be active");
        }
        this.status = technician.status();
        this.statusChangedAt = technician.statusChangedAt();
        if (unassignCategory) {
            this.technicianCategoryId = null;
        }
    }

    void deactivate(Technician technician) {
        if (technician.status() != TechnicianStatus.DEACTIVATED) {
            throw new IllegalArgumentException("deactivated technician must be deactivated");
        }
        this.status = technician.status();
        this.statusChangedAt = technician.statusChangedAt();
    }

    Technician toDomain() {
        return Technician.reconstitute(
                new TechnicianId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                status,
                joinedAt,
                statusChangedAt);
    }
}
