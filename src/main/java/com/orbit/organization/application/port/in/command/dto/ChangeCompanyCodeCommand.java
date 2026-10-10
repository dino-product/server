package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 새 코드는 서버가 정하므로 입력값이 없다. */
public record ChangeCompanyCodeCommand(Long accountId, Long organizationId) {}
