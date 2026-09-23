package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;

public interface UpdateWorkDetailsUseCase {

    void update(UpdateWorkDetailsCommand command);
}
