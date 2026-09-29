package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

@Repository
class OrganizationPersistenceAdapter implements OrganizationRepository, MembershipRepository {
    private final EntityManager entityManager;
    private final SpringDataOrganizationRepository organizations;
    private final SpringDataMembershipRepository memberships;

    OrganizationPersistenceAdapter(
            EntityManager entityManager,
            SpringDataOrganizationRepository organizations,
            SpringDataMembershipRepository memberships) {
        this.entityManager = entityManager;
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    public void save(Organization organization) {
        entityManager.persist(OrganizationJpaEntity.from(organization));
    }

    @Override
    public void update(Organization organization) {
        var entity = organizations.findById(organization.id().value()).orElseThrow();
        entity.updateFrom(organization);
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return organizations.findById(id.value()).map(OrganizationJpaEntity::toDomain);
    }

    @Override
    public void save(Membership membership) {
        entityManager.persist(MembershipJpaEntity.from(membership));
    }

    @Override
    public Optional<Membership> findByOrganizationAndAccount(OrganizationId organizationId, AuthAccountId accountId) {
        return memberships
                .findByOrganizationIdAndAuthAccountId(organizationId.value(), accountId.value())
                .map(MembershipJpaEntity::toDomain);
    }
}
