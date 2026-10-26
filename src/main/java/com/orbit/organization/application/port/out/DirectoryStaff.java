package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;

/** {@code staffCategory}는 직원 유형이 없거나 삭제됐으면 null이다. */
public record DirectoryStaff(
        MembershipId membershipId, AccountId accountId, boolean owner, DirectoryCategory staffCategory) {}
