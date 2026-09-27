package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.Industry;

public record OrganizationDetailsInfo(Long organizationId, String name, Industry industry) {}
