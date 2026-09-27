package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.Industry;

public record CreateOrganizationCommand(Long accountId, String name, Industry industry) {}
