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
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.persistence.BaseTimeEntity;

/** 직원 소속. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 소속은 1행이다. */
@Entity
@Table(name = "company_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "member_id"}))
class MembershipJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    /** 직원 유형. NULL은 "유형 없음"이다. 소속 도메인에 없어 지정·해제는 저장 어댑터가 이 컬럼만 바꾼다. */
    @Column(name = "company_membership_category_id")
    private Long staffCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected MembershipJpaEntity() {}

    private MembershipJpaEntity(Membership membership) {
        this.organizationId = membership.organizationId().value();
        this.accountId = membership.accountId().value();
        this.owner = membership.isOwner();
        this.status = membership.status();
        this.joinedAt = membership.joinedAt();
        this.statusChangedAt = membership.statusChangedAt();
    }

    static MembershipJpaEntity from(Membership membership) {
        if (membership.id().isPresent()) {
            throw new IllegalArgumentException("only new memberships can be saved");
        }
        return new MembershipJpaEntity(membership);
    }

    void changeOwner(boolean owner) {
        this.owner = owner;
    }

    void changeCategory(Long staffCategoryId) {
        this.staffCategoryId = staffCategoryId;
    }

    void reactivate(Membership membership, boolean unassignCategory) {
        if (membership.status() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("reactivated membership must be active");
        }
        this.owner = membership.isOwner();
        this.status = membership.status();
        this.statusChangedAt = membership.statusChangedAt();
        if (unassignCategory) {
            this.staffCategoryId = null;
        }
    }

    Membership toDomain() {
        return Membership.reconstitute(
                new MembershipId(id),
                new OrganizationId(organizationId),
                new AccountId(accountId),
                owner,
                status,
                joinedAt,
                statusChangedAt);
    }
}
