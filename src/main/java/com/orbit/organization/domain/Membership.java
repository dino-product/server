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
    private final Instant withdrawnAt;

    private Membership(
            MembershipId id,
            OrganizationId organizationId,
            AccountId accountId,
            boolean owner,
            MembershipStatus status,
            Instant joinedAt,
            Instant statusChangedAt,
            Instant withdrawnAt) {
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
        if (withdrawnAt != null && status != MembershipStatus.DEACTIVATED) {
            throw new IllegalArgumentException("withdrawn membership must be deactivated");
        }
        this.withdrawnAt = withdrawnAt;
    }

    /** 발주사를 만든 계정의 최초 소속. 활성 직원 소속이며 총관리자로 표시한다. */
    public static Membership founder(OrganizationId organizationId, AccountId accountId, Instant joinedAt) {
        return new Membership(null, organizationId, accountId, true, MembershipStatus.ACTIVE, joinedAt, joinedAt, null);
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
        return new Membership(
                null, organizationId, accountId, false, MembershipStatus.ACTIVE, joinedAt, activatedAt, null);
    }

    public static Membership reconstitute(
            MembershipId id,
            OrganizationId organizationId,
            AccountId accountId,
            boolean owner,
            MembershipStatus status,
            Instant joinedAt,
            Instant statusChangedAt) {
        return reconstitute(id, organizationId, accountId, owner, status, joinedAt, statusChangedAt, null);
    }

    /** 탈퇴 표시까지 복원한다. {@code withdrawnAt}이 없으면 탈퇴로 비활성화되지 않은 소속이다. */
    public static Membership reconstitute(
            MembershipId id,
            OrganizationId organizationId,
            AccountId accountId,
            boolean owner,
            MembershipStatus status,
            Instant joinedAt,
            Instant statusChangedAt,
            Instant withdrawnAt) {
        return new Membership(
                Objects.requireNonNull(id, "id must not be null"),
                organizationId,
                accountId,
                owner,
                status,
                joinedAt,
                statusChangedAt,
                withdrawnAt);
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
        return new Membership(id, organizationId, accountId, true, status, joinedAt, statusChangedAt, withdrawnAt);
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
        return new Membership(id, organizationId, accountId, false, status, joinedAt, statusChangedAt, withdrawnAt);
    }

    /**
     * 이 소속이 발주사의 유일한 활성 총관리자인지. 비활성 소속은 총관리자 표시가 남아 있어도 아니다.
     *
     * @param activeOwners 이 소속을 포함한 같은 발주사의 활성 총관리자 수
     */
    public boolean isSoleOwner(long activeOwners) {
        return status == MembershipStatus.ACTIVE && owner && activeOwners <= 1;
    }

    /**
     * 비활성 소속을 참여 요청 승인으로 다시 활성으로 바꾼다. 참여일은 유지하고, 승인으로 받는 역할은 직원이므로 총관리자 표시는 끈다([조직·계정] §6).
     *
     * @throws IllegalStateException 활성 소속이거나 탈퇴로 비활성화된 소속일 때
     */
    public Membership rejoin(Instant rejoinedAt) {
        if (status != MembershipStatus.DEACTIVATED) {
            throw new IllegalStateException("only deactivated memberships can rejoin");
        }
        if (isWithdrawn()) {
            throw new IllegalStateException("withdrawn memberships cannot rejoin");
        }
        return new Membership(
                id, organizationId, accountId, false, MembershipStatus.ACTIVE, joinedAt, rejoinedAt, null);
    }

    /**
     * 계정 탈퇴를 반영한다. 활성 소속은 비활성으로 바꾸고, 이미 비활성인 소속은 상태와 상태 변경 시각을 그대로 둔 채 탈퇴 표시만 남긴다. 탈퇴 표시가 있으면
     * 그대로 돌려준다(같은 탈퇴를 다시 받아도 결과가 같다). 총관리자 표시는 남기며, 비활성이라 활성 총관리자 수에 들지 않는다. 탈퇴 표시가 있는 소속은 다시
     * 활성이 되지 않는다([조직·계정] 기능명세 §6).
     */
    public Membership withdraw(Instant withdrawnAt) {
        Objects.requireNonNull(withdrawnAt, "withdrawnAt must not be null");
        if (isWithdrawn()) {
            return this;
        }
        if (status == MembershipStatus.ACTIVE) {
            return new Membership(
                    id,
                    organizationId,
                    accountId,
                    owner,
                    MembershipStatus.DEACTIVATED,
                    joinedAt,
                    withdrawnAt,
                    withdrawnAt);
        }
        return new Membership(id, organizationId, accountId, owner, status, joinedAt, statusChangedAt, withdrawnAt);
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public Optional<Instant> withdrawnAt() {
        return Optional.ofNullable(withdrawnAt);
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
