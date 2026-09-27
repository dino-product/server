package com.orbit.organization.application.port.in.command.dto;

public record ActivateTechnicianTypeCommand(Long accountId, Long organizationId, Long typeId) {}
