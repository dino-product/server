package com.orbit.organization.application.port.in.command.dto;

public record ActivateStaffTypeCommand(Long accountId, Long organizationId, Long typeId) {}
