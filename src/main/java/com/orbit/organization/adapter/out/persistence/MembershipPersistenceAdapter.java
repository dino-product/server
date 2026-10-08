package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

    private final SpringDataMembershipRepository repository;

    MembershipPersistenceAdapter(SpringDataMembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public Membership save(Membership membership) {
        return repository.save(MembershipJpaEntity.from(membership)).toDomain();
    }

    @Override
    public Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountIdAndStatus(
                        organizationId.value(), accountId.value(), MembershipStatus.ACTIVE)
                .map(MembershipJpaEntity::toDomain);
    }
}
