package com.example.auth.application.port.in.command;

import com.example.auth.application.port.in.command.dto.RecordUserRegistrationCommand;

public interface RecordUserRegistrationUseCase {
    void record(RecordUserRegistrationCommand command);
}
