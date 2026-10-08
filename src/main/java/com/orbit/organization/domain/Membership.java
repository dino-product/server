package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 계정의 발주사 직원 소속. 총관리자는 별도 역할이 아니라 총관리자 표시가 있는 직원 소속이다. */
public final class Membership {

    private final MembershipId id;
    private final OrganizationId organizationId;
    private final AccountId accountId;
    private final boolean owner;
    private final MembershipStatus status;
    private final Instant joinedAt;
    private final Instant statusChangedAt;

    private Membership(
            MembershipId id,
            OrganizationId organizationId,
            AccountId accountId,
            boolean owner,
            MembershipStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.owner = owner;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt must not be null");
        if (statusChangedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("statusChangedAt must not be before joinedAt");
        }
    }

    /** 발주사를 만든 계정의 최초 소속. 활성 직원 소속이며 총관리자로 표시한다. */
    public static Membership founder(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return new Membership(null, organizationId, accountId, true, MembershipStatus.ACTIVE, joinedAt, joinedAt);
    }

    public static Membership reconstitute(
            MembershipId id,
            OrganizationId organizationId,
            AccountId accountId,
            boolean owner,
            MembershipStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        return new Membership(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                owner,
                status,
                joinedAt,
                statusChangedAt);
    }

    /** 같은 발주사의 다른 총관리자는 그대로 두고 이 소속에 총관리자 표시를 붙인다. 이미 총관리자면 그대로 돌려준다. */
    public Membership designateAsOwner() {
        if (owner) {
            return this;
        }
        return new Membership(id, organizationId, accountId, true, status, joinedAt, statusChangedAt);
    }

    public Optional<MembershipId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public AccountId accountId() {
        return accountId;
    }

    public boolean isOwner() {
        return owner;
    }

    public MembershipStatus status() {
        return status;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }
}
