package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;

public interface CancelWorkUseCase {

    void cancel(CancelWorkCommand command);
}
