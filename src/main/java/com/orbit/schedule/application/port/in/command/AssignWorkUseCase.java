package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.AssignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

public interface AssignWorkUseCase {

    ScheduleChangeInfo assign(AssignWorkCommand command);
}
