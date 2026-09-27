package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.TechnicianTypeId;

public interface TechnicianTypeUsagePort {
    boolean isAssigned(TechnicianTypeId id);
}
