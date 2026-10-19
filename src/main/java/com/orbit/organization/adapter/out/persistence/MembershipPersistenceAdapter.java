package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

/** 저장된 소속을 다시 저장하면 총관리자 표시만 옮기고, 재활성화는 {@link #reactivate}로만 한다. 수정 시각은 감사 컬럼이 채운다. */
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
    public Optional<Membership> find(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountId(organizationId.value(), accountId.value())
                .map(MembershipJpaEntity::toDomain);
    }

    @Override
    public Membership reactivate(Membership membership, boolean unassignCategory) {
        MembershipJpaEntity entity = repository
                .findById(membership.id().orElseThrow().value())
                .orElseThrow(() -> new IllegalStateException("membership to reactivate must exist"));
        entity.reactivate(membership, unassignCategory);
        return entity.toDomain();
    }

    @Override
    public Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId) {
        return repository
                .findByOrganizationIdAndAccountIdAndStatus(
                        organizationId.value(), accountId.value(), MembershipStatus.ACTIVE)
                .map(MembershipJpaEntity::toDomain);
    }

    @Override
    public boolean existsActive(OrganizationId organizationId, AccountId accountId) {
        return repository.existsByOrganizationIdAndAccountIdAndStatus(
                organizationId.value(), accountId.value(), MembershipStatus.ACTIVE);
    }

    @Override
    public int unassignStaffCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        return repository.unassignStaffCategory(organizationId.value(), categoryId.value(), unassignedAt);
    }

    @Override
    public void changeStaffCategory(MembershipId id, MemberCategoryId categoryId) {
        repository
                .findById(id.value())
                .orElseThrow(() -> new IllegalStateException("membership to change must exist"))
                .changeCategory(categoryId == null ? null : categoryId.value());
    }

    @Override
    public long countActiveOwners(OrganizationId organizationId) {
        return repository.countByOrganizationIdAndOwnerTrueAndStatus(organizationId.value(), MembershipStatus.ACTIVE);
    }
}
