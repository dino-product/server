package com.orbit.organization.application.port.in.command.dto;

public record DeactivateTechnicianTypeCommand(Long accountId, Long organizationId, Long typeId) {}
