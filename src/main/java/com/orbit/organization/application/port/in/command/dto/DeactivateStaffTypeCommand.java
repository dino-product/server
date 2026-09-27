package com.orbit.organization.application.port.in.command.dto;

public record DeactivateStaffTypeCommand(Long accountId, Long organizationId, Long typeId) {}
