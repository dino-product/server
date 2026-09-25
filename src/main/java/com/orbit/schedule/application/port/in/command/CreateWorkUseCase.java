package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;

public interface CreateWorkUseCase {

    CreatedWorkInfo create(CreateWorkCommand command);
}
