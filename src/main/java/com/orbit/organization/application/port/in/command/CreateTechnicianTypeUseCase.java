package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CreateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;

public interface CreateTechnicianTypeUseCase {
    TechnicianTypeInfo create(CreateTechnicianTypeCommand command);
}
