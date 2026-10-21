package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.TechnicianId;

/** {@code technicianCategory}는 기사 유형이 없거나 삭제됐으면 null이다. */
public record DirectoryTechnician(
        TechnicianId technicianId, AccountId accountId, DirectoryCategory technicianCategory) {}
