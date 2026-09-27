package com.orbit.organization.application.port.in.command.dto;

public record UpdateStaffTypeCommand(Long accountId, Long organizationId, Long typeId, String name, int color) {}
