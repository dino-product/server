package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;

public interface RejectWorkUseCase {

    void reject(RejectWorkCommand command);
}
