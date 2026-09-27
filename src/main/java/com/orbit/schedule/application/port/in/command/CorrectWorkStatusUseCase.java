package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.CorrectWorkStatusCommand;

public interface CorrectWorkStatusUseCase {

    void correct(CorrectWorkStatusCommand command);
}
