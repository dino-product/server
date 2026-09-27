package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeactivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;

public interface DeactivateTechnicianTypeUseCase {
    TechnicianTypeInfo deactivate(DeactivateTechnicianTypeCommand command);
}
