package com.example.auth.application.port.in.command.dto;

import java.time.Instant;

public record RecordUserRegistrationCommand(Long userId, Instant occurredAt) {}
