package com.orbit.organization.application.port.in.query.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. */
public record GetOrganizationQuery(Long accountId, Long organizationId) {}
