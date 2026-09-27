package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;

public interface UpdateTechnicianTypeUseCase {
    TechnicianTypeInfo update(UpdateTechnicianTypeCommand command);
}
