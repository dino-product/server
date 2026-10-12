package com.orbit.profile.application.port.in.command.dto;

public record WithdrawCommand(Long requesterAccountId, Long accountId) {}
