package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeleteTechnicianTypeCommand;

public interface DeleteTechnicianTypeUseCase {
    void delete(DeleteTechnicianTypeCommand command);
}
