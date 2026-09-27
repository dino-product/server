package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeactivateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;

public interface DeactivateStaffTypeUseCase {
    StaffTypeInfo deactivate(DeactivateStaffTypeCommand command);
}
