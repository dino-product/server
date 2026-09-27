package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;

public interface OrganizationIdentityPort {
    OrganizationId nextOrganizationId();

    MembershipId nextMembershipId();
}
