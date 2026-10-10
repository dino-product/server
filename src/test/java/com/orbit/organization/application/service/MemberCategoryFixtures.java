package com.orbit.organization.application.service;

import java.time.Instant;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;

/** 유형 서비스 테스트가 함께 쓰는 고정 값. 요청자는 계정 7, 발주사 10이다. */
final class MemberCategoryFixtures {

    static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
    static final AccountId ACCOUNT_ID = new AccountId(7L);
    static final String BLUE = "#3B82F6";
    static final String RED = "#EF4444";

    private MemberCategoryFixtures() {}

    static Membership requester(boolean owner) {
        return Membership.reconstitute(
                new MembershipId(3L),
                ORGANIZATION_ID,
                ACCOUNT_ID,
                owner,
                MembershipStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT);
    }

    static MemberCategory category(long id, MemberCategoryKind kind, String name) {
        return category(id, ORGANIZATION_ID, kind, name);
    }

    static MemberCategory category(long id, OrganizationId organizationId, MemberCategoryKind kind, String name) {
        return MemberCategory.reconstitute(
                new MemberCategoryId(id),
                organizationId,
                kind,
                new CategoryName(name),
                new CategoryColor(BLUE),
                CREATED_AT,
                CREATED_AT,
                false,
                null);
    }

    static MemberCategory deletedCategory(long id, MemberCategoryKind kind, String name) {
        MemberCategory category = category(id, kind, name);
        category.delete(CREATED_AT);
        return category;
    }
}
