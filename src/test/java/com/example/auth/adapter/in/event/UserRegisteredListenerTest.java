package com.example.auth.adapter.in.event;

import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.auth.application.port.in.command.RecordUserRegistrationUseCase;
import com.example.auth.application.port.in.command.dto.RecordUserRegistrationCommand;
import com.example.user.UserRegistered;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {
    @Mock
    private RecordUserRegistrationUseCase useCase;

    @Test
    void convertsPublicEventIntoAuthCommand() {
        Instant occurredAt = Instant.parse("2026-09-04T00:00:00Z");
        new UserRegisteredListener(useCase).on(new UserRegistered(1L, occurredAt));
        verify(useCase).record(new RecordUserRegistrationCommand(1L, occurredAt));
    }
}
