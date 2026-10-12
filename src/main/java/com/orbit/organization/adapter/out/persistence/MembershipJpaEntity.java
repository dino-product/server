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

/** 직원 소속. 발주사·계정은 다른 애그리게잇·모듈이므로 ID로만 둔다. 같은 계정·같은 발주사의 소속은 1행이다. */
@Entity
@Table(name = "company_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "member_id"}))
class MembershipJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MembershipJpaEntity() {}

    private MembershipJpaEntity(Membership membership) {
        this.organizationId = membership.organizationId().value();
        this.accountId = membership.accountId().value();
        this.owner = membership.isOwner();
        this.status = membership.status();
        this.joinedAt = membership.joinedAt();
        this.statusChangedAt = membership.statusChangedAt();
        this.createdAt = membership.joinedAt();
        this.updatedAt = membership.statusChangedAt();
    }

    static MembershipJpaEntity from(Membership membership) {
        if (membership.id().isPresent()) {
            throw new IllegalArgumentException("only new memberships can be saved");
        }
        return new MembershipJpaEntity(membership);
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
