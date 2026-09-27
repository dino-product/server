package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ActivateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;

public interface ActivateStaffTypeUseCase {
    StaffTypeInfo activate(ActivateStaffTypeCommand command);
}
