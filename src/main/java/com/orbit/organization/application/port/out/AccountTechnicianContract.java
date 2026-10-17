package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

/** 계정의 기사 계약 한 행. 역할 변경·비활성화로 끝난 계약도 비활성으로 들어 있다. */
public record AccountTechnicianContract(TechnicianId technicianId, OrganizationId organizationId, boolean active) {}
