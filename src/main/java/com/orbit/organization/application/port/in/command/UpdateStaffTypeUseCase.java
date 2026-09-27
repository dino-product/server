package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;

public interface UpdateStaffTypeUseCase {
    StaffTypeInfo update(UpdateStaffTypeCommand command);
}
