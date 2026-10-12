package com.orbit.organization;

/** 활성 직원 소속. {@code owner}는 총관리자 표시이며 한 발주사에 여러 명일 수 있다. */
public record StaffMember(Long membershipId, Long organizationId, boolean owner) implements OrganizationMember {}
