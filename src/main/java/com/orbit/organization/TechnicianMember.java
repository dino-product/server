package com.orbit.organization;

/** 활성 기사 계약. {@code technicianId}는 역할 변경으로 끝났다가 다시 활성이 되어도 바뀌지 않는다. */
public record TechnicianMember(Long technicianId, Long organizationId) implements OrganizationMember {}
