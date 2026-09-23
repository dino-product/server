package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;

public interface ReassignWorkUseCase {

    ScheduleChangeResult reassign(ReassignWorkCommand command);
}
