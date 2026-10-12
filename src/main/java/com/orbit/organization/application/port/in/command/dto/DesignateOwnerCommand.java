package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정, {@code membershipId}는 총관리자로 지정할 직원 소속이다. */
public record DesignateOwnerCommand(Long accountId, Long organizationId, Long membershipId) {}
