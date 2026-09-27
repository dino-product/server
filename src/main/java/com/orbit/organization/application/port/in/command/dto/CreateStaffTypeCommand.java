package com.orbit.organization.application.port.in.command.dto;

public record CreateStaffTypeCommand(Long accountId, Long organizationId, String name, int color) {}
