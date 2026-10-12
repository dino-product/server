package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.TechnicianId;

/** 활성 기사 계약. */
public record ActiveTechnicianContract(TechnicianId technicianId) implements ActiveMember {}
