package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;

public interface TechnicianRepository {

    Optional<Technician> findActive(OrganizationId organizationId, AccountId accountId);
}
