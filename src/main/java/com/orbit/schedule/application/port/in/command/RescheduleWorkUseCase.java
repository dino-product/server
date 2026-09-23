package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult;

public interface RescheduleWorkUseCase {

    ScheduleChangeResult reschedule(RescheduleWorkCommand command);
}
