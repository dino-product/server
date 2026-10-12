package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.MembershipId;

/** 활성 직원 소속. {@code owner}는 총관리자 표시다. */
public record ActiveMembership(MembershipId membershipId, boolean owner) implements ActiveMember {}
