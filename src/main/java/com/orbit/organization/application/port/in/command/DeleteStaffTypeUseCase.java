package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeleteStaffTypeCommand;

public interface DeleteStaffTypeUseCase {
    void delete(DeleteStaffTypeCommand command);
}
