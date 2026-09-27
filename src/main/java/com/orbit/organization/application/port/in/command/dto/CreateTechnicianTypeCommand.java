package com.orbit.organization.application.port.in.command.dto;

public record CreateTechnicianTypeCommand(Long accountId, Long organizationId, String name, int color) {}
