package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.Industry;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. */
public record CreateOrganizationCommand(Long accountId, String name, Industry industry) {}
