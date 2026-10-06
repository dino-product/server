package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.Membership;

public interface MembershipRepository {

    Membership save(Membership membership);
}
