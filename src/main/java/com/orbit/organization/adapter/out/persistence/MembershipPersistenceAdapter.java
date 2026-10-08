package com.orbit.organization.adapter.out.persistence;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

/** 저장된 소속을 다시 저장하면 수정 시각을 주입 시계로 찍는다. 감사 컬럼 공통 처리가 생기면 그쪽으로 옮긴다. */
@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

    private final SpringDataMembershipRepository repository;
    private final Clock clock;

    MembershipPersistenceAdapter(SpringDataMembershipRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Membership save(Membership membership) {
        if (membership.id().isEmpty()) {
            return repository.save(MembershipJpaEntity.from(membership)).toDomain();
        }
        MembershipJpaEntity entity = repository
                .findById(membership.id().get().value())
                .orElseThrow(() -> new IllegalStateException("membership to update must exist"));
        entity.changeOwner(membership.isOwner(), clock.instant());
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
}
