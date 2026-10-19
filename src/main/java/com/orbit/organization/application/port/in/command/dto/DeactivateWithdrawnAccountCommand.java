package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 탈퇴가 커밋된 계정이다. */
public record DeactivateWithdrawnAccountCommand(Long accountId) {}
