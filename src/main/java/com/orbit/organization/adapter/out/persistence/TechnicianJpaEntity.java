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
 * 계약은 참여 요청 승인이 만들고, 기사 유형은 유형 지정 작업이 도메인과 함께 연결한다(지금은 기사 유형 삭제의 벌크 해제와 재참여 승인의 해제만 한다). 활성
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

    /** 계정 탈퇴로 비활성화된 시각. NULL이면 탈퇴로 비활성화되지 않았다. 탈퇴 표시가 있는 행은 다시 활성이 되지 않는다. */
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected TechnicianJpaEntity() {}

    private TechnicianJpaEntity(Technician technician) {
        this.organizationId = technician.organizationId().value();
        this.accountId = technician.accountId().value();
        this.status = technician.status();
        this.joinedAt = technician.joinedAt();
        this.statusChangedAt = technician.statusChangedAt();
        this.withdrawnAt = technician.withdrawnAt().orElse(null);
    }

    static TechnicianJpaEntity from(Technician technician) {
        if (technician.id().isPresent()) {
            throw new IllegalArgumentException("only new technicians can be saved");
        }
        return new TechnicianJpaEntity(technician);
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

    void withdraw(Technician technician) {
        if (!technician.isWithdrawn()) {
            throw new IllegalArgumentException("withdrawn technician must have withdrawal time");
        }
        this.status = technician.status();
        this.statusChangedAt = technician.statusChangedAt();
        this.withdrawnAt = technician.withdrawnAt().orElseThrow();
    }

    Technician toDomain() {
        return Technician.reconstitute(
                new TechnicianId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                status,
                joinedAt,
                statusChangedAt,
                withdrawnAt);
    }
}
