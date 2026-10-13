package com.orbit.organization.application.port.in.command.dto;

public record CancelParticipationRequestCommand(Long accountId, Long requestId) {}
