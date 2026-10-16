package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand;
import com.orbit.schedule.application.port.in.command.dto.TechnicianWorkReleaseInfo;

public interface ReleaseTechnicianWorksUseCase {

    TechnicianWorkReleaseInfo release(ReleaseTechnicianWorksCommand command);
}
