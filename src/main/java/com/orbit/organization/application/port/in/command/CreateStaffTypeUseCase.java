package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CreateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;

public interface CreateStaffTypeUseCase {
    StaffTypeInfo create(CreateStaffTypeCommand command);
}
