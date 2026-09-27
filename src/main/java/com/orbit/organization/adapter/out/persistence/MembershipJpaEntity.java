package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.StaffTypeId;

@Entity
@Table(
        name = "membership",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_membership_organization_account",
                        columnNames = {"organization_id", "auth_account_id"}))
class MembershipJpaEntity {
    @Id
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "auth_account_id", nullable = false)
    private Long authAccountId;

    @Column(name = "staff_type_id")
    private Long staffTypeId;

    @Column(name = "joined_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
    private Instant joinedAt;

    @Column(nullable = false)
    private boolean active;

    protected MembershipJpaEntity() {}

    private MembershipJpaEntity(Membership membership) {
        id = membership.id().value();
        organizationId = membership.organizationId().value();
        authAccountId = membership.authAccountId().value();
        staffTypeId = membership.typeId() == null ? null : membership.typeId().value();
        joinedAt = membership.joinedAt();
        active = membership.active();
    }

    static MembershipJpaEntity from(Membership membership) {
        return new MembershipJpaEntity(membership);
    }

    Membership toDomain() {
        return Membership.reconstitute(
                new MembershipId(id),
                new OrganizationId(organizationId),
                new AuthAccountId(authAccountId),
                staffTypeId == null ? null : new StaffTypeId(staffTypeId),
                joinedAt,
                active);
    }
}
