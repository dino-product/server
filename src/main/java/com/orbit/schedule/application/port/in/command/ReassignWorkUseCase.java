package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.ReassignWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

public interface ReassignWorkUseCase {

    ScheduleChangeInfo reassign(ReassignWorkCommand command);
}
