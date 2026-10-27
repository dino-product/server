package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code color}는 프리셋 팔레트의 {@code #RRGGBB} 값이다. */
public record CreateWorkTypeCommand(Long accountId, Long organizationId, String name, String color) {}
