package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.RescheduleWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeInfo;

public interface RescheduleWorkUseCase {

    ScheduleChangeInfo reschedule(RescheduleWorkCommand command);
}
