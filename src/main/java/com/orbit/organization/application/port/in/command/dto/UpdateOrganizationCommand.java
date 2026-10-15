package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.Industry;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 발주사명·업종을 함께 교체한다. */
public record UpdateOrganizationCommand(Long accountId, Long organizationId, String name, Industry industry) {}
