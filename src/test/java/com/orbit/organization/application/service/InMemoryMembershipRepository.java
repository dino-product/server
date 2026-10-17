package com.orbit.organization.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

/** 직원 소속 저장소의 메모리 fake. 저장 순서대로 1부터 식별자를 붙이고, 식별자가 있는 소속은 같은 행을 덮어쓴다. */
class InMemoryMembershipRepository implements MembershipRepository {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final Map<MembershipId, Membership> memberships = new LinkedHashMap<>();
    private long nextId = 1;
    private int saveCount;

    /** 저장소에 이미 있는 활성 소속을 둔다. 저장 횟수에 넣지 않는다. */
    Membership given(OrganizationId organizationId, AccountId accountId, boolean owner) {
        return given(organizationId, accountId, owner, MembershipStatus.ACTIVE);
    }

    Membership given(OrganizationId organizationId, AccountId accountId, boolean owner, MembershipStatus status) {
        Membership membership = Membership.reconstitute(
                new MembershipId(nextId++), organizationId, accountId, owner, status, JOINED_AT, JOINED_AT);
        memberships.put(membership.id().orElseThrow(), membership);
        return membership;
    }

    /** 다른 트랜잭션이 커밋한 비활성화처럼 저장된 소속을 비활성으로 바꾼다. 저장 횟수에 넣지 않는다. */
    void deactivate(MembershipId id) {
        Membership current = memberships.get(id);
        memberships.put(
                id,
                Membership.reconstitute(
                        id,
                        current.organizationId(),
                        current.accountId(),
                        current.isOwner(),
                        MembershipStatus.DEACTIVATED,
                        current.joinedAt(),
                        current.statusChangedAt()));
    }

    Membership stored(MembershipId id) {
        return memberships.get(id);
    }

    int saveCount() {
        return saveCount;
    }

    @Override
    public Membership save(Membership membership) {
        saveCount++;
        Membership saved = membership.id().isPresent()
                ? membership
                : Membership.reconstitute(
                        new MembershipId(nextId++),
                        membership.organizationId(),
                        membership.accountId(),
                        membership.isOwner(),
                        membership.status(),
                        membership.joinedAt(),
                        membership.statusChangedAt());
        memberships.put(saved.id().orElseThrow(), saved);
        return saved;
    }

    @Override
    public Optional<Membership> findById(MembershipId id) {
        return Optional.ofNullable(memberships.get(id));
    }

    @Override
    public Optional<Membership> findActive(OrganizationId organizationId, AccountId accountId) {
        return memberships.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .filter(membership -> membership.accountId().equals(accountId))
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public boolean existsActive(OrganizationId organizationId, AccountId accountId) {
        return findActive(organizationId, accountId).isPresent();
    }

    @Override
    public long countActiveOwners(OrganizationId organizationId) {
        return memberships.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .filter(Membership::isOwner)
                .count();
    }

    /** 직원 유형은 소속 도메인에 없어 이 fake로는 다루지 않는다. 유형 삭제 테스트는 저장소 mock을 쓴다. */
    @Override
    public int unassignStaffCategory(OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        throw new UnsupportedOperationException("staff categories are not modeled by this fake");
    }
}
