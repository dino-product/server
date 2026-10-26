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

    /** 참여 요청 승인으로 생기는 활성 직원 소속. 총관리자 표시가 없고 참여일은 승인 시각이다. */
    public static Membership join(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return join(organizationId, accountId, joinedAt, joinedAt);
    }

    /**
     * 같은 발주사에 기사로 있던 회원을 직원으로 승인해 새로 만드는 활성 직원 소속. 참여일은 이전 역할의 참여일을 이어받고 상태 변경 시각은 승인 시각이다.
     */
    public static Membership join(
            OrganizationId organizationId, AccountId accountId, Instant joinedAt, Instant activatedAt) {
        return new Membership(null, organizationId, accountId, false, MembershipStatus.ACTIVE, joinedAt, activatedAt);
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

    /**
     * 같은 발주사의 다른 총관리자는 그대로 두고 이 소속에 총관리자 표시를 붙인다. 이미 총관리자면 그대로 돌려준다.
     *
     * @throws InactiveMembershipException 활성 소속이 아닐 때([조직·계정] §4.1)
     */
    public Membership designateAsOwner() {
        if (status != MembershipStatus.ACTIVE) {
            throw new InactiveMembershipException();
        }
        if (owner) {
            return this;
        }
        return new Membership(id, organizationId, accountId, true, status, joinedAt, statusChangedAt);
    }

    /**
     * 총관리자 표시를 뗀다. 총관리자가 아니면 그대로 돌려준다.
     *
     * @param activeOwners 이 소속을 포함한 같은 발주사의 활성 총관리자 수
     * @throws LastOwnerException 이 소속이 발주사의 마지막 총관리자일 때
     */
    public Membership revokeOwner(long activeOwners) {
        if (!owner) {
            return this;
        }
        if (activeOwners <= 1) {
            throw new LastOwnerException();
        }
        return new Membership(id, organizationId, accountId, false, status, joinedAt, statusChangedAt);
    }

    /**
     * 비활성 소속을 다시 활성으로 바꾼다(참여 요청 승인, 총관리자의 재활성화). 참여일은 유지하고 총관리자 표시는 끈다([조직·계정] §6, 비활성화 때 이미 뗀다).
     *
     * @throws IllegalStateException 활성 소속일 때
     */
    public Membership reactivate(Instant reactivatedAt) {
        if (status != MembershipStatus.DEACTIVATED) {
            throw new IllegalStateException("only deactivated memberships can be reactivated");
        }
        return new Membership(id, organizationId, accountId, false, MembershipStatus.ACTIVE, joinedAt, reactivatedAt);
    }

    /**
     * 총관리자가 이 소속을 비활성화한다([조직·계정] §4.1). 소속을 지우지 않고 상태만 바꾸며 참여일은 유지한다. 총관리자 표시도 떼므로 다시 활성이 되면 직원이다.
     *
     * @param activeOwners 이 소속을 포함한 같은 발주사의 활성 총관리자 수
     * @throws LastOwnerException 이 소속이 발주사의 마지막 총관리자일 때([조직·계정] §11)
     * @throws IllegalStateException 이미 비활성 소속일 때
     */
    public Membership deactivate(Instant deactivatedAt, long activeOwners) {
        Instant at = Objects.requireNonNull(deactivatedAt, "deactivatedAt must not be null");
        if (status != MembershipStatus.ACTIVE) {
            throw new IllegalStateException("only active memberships can be deactivated");
        }
        if (owner && activeOwners <= 1) {
            throw new LastOwnerException();
        }
        return new Membership(id, organizationId, accountId, false, MembershipStatus.DEACTIVATED, joinedAt, at);
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
