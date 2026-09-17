package com.example.auth.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.example.auth.application.port.in.command.RecordUserRegistrationUseCase;
import com.example.auth.application.port.in.command.dto.RecordUserRegistrationCommand;
import com.example.user.UserRegistered;

@Component
public class UserRegisteredListener {
    private final RecordUserRegistrationUseCase useCase;

    public UserRegisteredListener(RecordUserRegistrationUseCase useCase) {
        this.useCase = useCase;
    }

    @ApplicationModuleListener
    public void on(UserRegistered event) {
        useCase.record(new RecordUserRegistrationCommand(event.userId(), event.occurredAt()));
    }
}
