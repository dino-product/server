package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 이름·색상을 함께 교체한다. */
public record UpdateWorkTypeCommand(Long accountId, Long organizationId, Long workTypeId, String name, String color) {}
