package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.Industry;

public record UpdateOrganizationDetailsCommand(Long accountId, Long organizationId, String name, Industry industry) {}
