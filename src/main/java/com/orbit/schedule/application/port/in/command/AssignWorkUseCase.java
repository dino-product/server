package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;

public interface AssignWorkUseCase {

    ScheduleChangeResult assign(AssignWorkCommand command);
}
