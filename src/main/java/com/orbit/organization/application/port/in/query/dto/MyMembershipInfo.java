package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.MemberRole;

/** 내 소속 목록의 한 줄. 발주사마다 하나다. */
public record MyMembershipInfo(Long organizationId, String organizationName, MemberRole role, boolean active) {}
