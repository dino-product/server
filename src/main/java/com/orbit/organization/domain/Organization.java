package com.orbit.organization.domain;

/** 회사. 총관리자는 직원 소속 ID 하나로 표현하며 직원에 별도 ADMIN 상태를 두지 않는다. */
public final class Organization {
    private final OrganizationId id;
    private OrganizationName name;
    private Industry industry;
    private CompanyCode code;
    private MembershipId ownerMembershipId;

    private Organization(
            OrganizationId id, OrganizationName name, Industry industry, CompanyCode code, Membership owner) {
        if (id == null || name == null || code == null) {
            throw new OrganizationRuleViolation("organization id, name and code must not be null");
        }
        this.id = id;
        this.name = name;
        this.industry = industry;
        this.code = code;
        transferOwnership(owner);
    }

    public static Organization create(
            OrganizationId id, OrganizationName name, Industry industry, CompanyCode code, Membership owner) {
        return new Organization(id, name, industry, code, owner);
    }

    public void updateDetails(OrganizationName name, Industry industry) {
        if (name == null) {
            throw new OrganizationRuleViolation("organization name must not be null");
        }
        this.name = name;
        this.industry = industry;
    }

    public void changeCode(CompanyCode code) {
        if (code == null) {
            throw new OrganizationRuleViolation("company code must not be null");
        }
        this.code = code;
    }

    /** 호출자는 최신 직원 소속을 조회하고 위임 권한과 트랜잭션을 보장해야 한다. */
    public void transferOwnership(Membership successor) {
        requireOwnMembership(successor);
        if (!successor.active()) {
            throw new OrganizationRuleViolation("owner must be active");
        }
        ownerMembershipId = successor.id();
    }

    public boolean isManagedBy(Membership membership) {
        return membership != null
                && id.equals(membership.organizationId())
                && membership.active()
                && ownerMembershipId.equals(membership.id());
    }

    /** 현재 총관리자가 없는 상태로 만들 수 없다. 위임 후 기존 총관리자의 소속을 비활성화한다. */
    public void deactivateMembership(Membership membership) {
        requireOwnMembership(membership);
        if (ownerMembershipId.equals(membership.id())) {
            throw new OrganizationRuleViolation("transfer ownership before deactivating the owner");
        }
        membership.deactivate();
    }

    private void requireOwnMembership(Membership membership) {
        if (membership == null || !id.equals(membership.organizationId())) {
            throw new OrganizationRuleViolation("membership must belong to this organization");
        }
    }

    public OrganizationId id() {
        return id;
    }

    public OrganizationName name() {
        return name;
    }

    public Industry industry() {
        return industry;
    }

    public CompanyCode code() {
        return code;
    }

    public MembershipId ownerMembershipId() {
        return ownerMembershipId;
    }
}
