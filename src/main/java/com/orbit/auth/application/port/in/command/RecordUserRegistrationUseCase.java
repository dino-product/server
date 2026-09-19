package com.orbit.auth.application.port.in.command;

import com.orbit.auth.application.port.in.command.dto.RecordUserRegistrationCommand;

public interface RecordUserRegistrationUseCase {
    void record(RecordUserRegistrationCommand command);
}
