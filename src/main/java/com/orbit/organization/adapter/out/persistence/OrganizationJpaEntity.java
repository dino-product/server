package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;

@Entity
@Table(
        name = "organization",
        uniqueConstraints = @UniqueConstraint(name = "uq_organization_code", columnNames = "code"))
class OrganizationJpaEntity {
    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Industry industry;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "owner_membership_id", nullable = false)
    private Long ownerMembershipId;

    protected OrganizationJpaEntity() {}

    private OrganizationJpaEntity(Organization organization) {
        id = organization.id().value();
        updateFrom(organization);
    }

    static OrganizationJpaEntity from(Organization organization) {
        return new OrganizationJpaEntity(organization);
    }

    void updateFrom(Organization organization) {
        name = organization.name().value();
        industry = organization.industry();
        code = organization.code().value();
        ownerMembershipId = organization.ownerMembershipId().value();
    }

    Organization toDomain() {
        return Organization.reconstitute(
                new OrganizationId(id),
                OrganizationName.reconstitute(name),
                industry,
                CompanyCode.reconstitute(code),
                new MembershipId(ownerMembershipId));
    }
}
