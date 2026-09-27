package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.StaffTypeId;

public interface StaffTypeUsagePort {
    boolean isAssigned(StaffTypeId id);
}
