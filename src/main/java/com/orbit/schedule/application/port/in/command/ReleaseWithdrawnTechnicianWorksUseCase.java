package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.ReleaseWithdrawnTechnicianWorksCommand;

public interface ReleaseWithdrawnTechnicianWorksUseCase {

    void release(ReleaseWithdrawnTechnicianWorksCommand command);
}
