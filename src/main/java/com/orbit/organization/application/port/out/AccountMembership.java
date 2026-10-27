package com.orbit.organization.application.port.out;

import java.time.Instant;

import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;

/** 계정의 직원 소속 또는 기사 계약 한 행. 역할 변경으로 끝난 행도 비활성으로 들어 있다. {@code joinedAt}은 그 행의 참여일이다. */
public record AccountMembership(
        OrganizationId organizationId,
        String organizationName,
        MemberRole role,
        boolean active,
        Instant joinedAt,
        Instant statusChangedAt) {}
