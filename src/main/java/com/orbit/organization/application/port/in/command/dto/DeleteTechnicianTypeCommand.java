package com.orbit.organization.application.port.in.command.dto;

public record DeleteTechnicianTypeCommand(Long accountId, Long organizationId, Long typeId) {}
