package com.orbit.organization.application.port.in.command.dto;

public record DeleteStaffTypeCommand(Long accountId, Long organizationId, Long typeId) {}
