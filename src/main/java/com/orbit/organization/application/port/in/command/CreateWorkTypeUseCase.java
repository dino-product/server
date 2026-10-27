package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;

public interface CreateWorkTypeUseCase {

    WorkTypeInfo create(CreateWorkTypeCommand command);
}
