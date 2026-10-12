package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

/** 저장된 소속을 다시 저장하면 총관리자 표시만 옮긴다. 수정 시각은 감사 컬럼이 채운다. */
@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

    private final SpringDataMembershipRepository repository;

    MembershipPersistenceAdapter(SpringDataMembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public Membership save(Membership membership) {
        if (membership.id().isEmpty()) {
            return repository.save(MembershipJpaEntity.from(membership)).toDomain();
        }
        MembershipJpaEntity entity = repository
                .findById(membership.id().get().value())
                .orElseThrow(() -> new IllegalStateException("membership to update must exist"));
        entity.changeOwner(membership.isOwner());
        return entity.toDomain();
    }

    @Override
    public Optional<Membership> findById(MembershipId id) {
        return repository.findById(id.value()).map(MembershipJpaEntity::toDomain);
    }

    @Override
    public Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountIdAndStatus(
                        organizationId.value(), accountId.value(), MembershipStatus.ACTIVE)
                .map(MembershipJpaEntity::toDomain);
    }

    @Override
    public long countActiveOwners(OrganizationId organizationId) {
        return repository.countByOrganizationIdAndOwnerTrueAndStatus(organizationId.value(), MembershipStatus.ACTIVE);
    }
}
