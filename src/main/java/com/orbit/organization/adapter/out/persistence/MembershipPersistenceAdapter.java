package com.orbit.organization.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.Membership;

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
}
