package com.orbit.organization.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
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
    public long countActiveOwners(OrganizationId organizationId) {
        return memberships.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .filter(Membership::isOwner)
                .count();
    }
}
