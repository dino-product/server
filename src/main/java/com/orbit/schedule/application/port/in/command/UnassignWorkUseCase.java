package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;

public interface UnassignWorkUseCase {

    void unassign(UnassignWorkCommand command);
}
