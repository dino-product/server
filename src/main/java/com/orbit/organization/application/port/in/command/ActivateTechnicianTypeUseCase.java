package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ActivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;

public interface ActivateTechnicianTypeUseCase {
    TechnicianTypeInfo activate(ActivateTechnicianTypeCommand command);
}
