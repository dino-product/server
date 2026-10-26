package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;

public interface UpdateWorkTypeUseCase {

    WorkTypeInfo update(UpdateWorkTypeCommand command);
}
