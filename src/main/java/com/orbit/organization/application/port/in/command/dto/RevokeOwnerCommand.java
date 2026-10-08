package com.orbit.organization.application.port.in.command.dto;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정, {@code membershipId}는 총관리자를 해제할 직원 소속이다(요청자 본인일 수 있다). */
public record RevokeOwnerCommand(Long accountId, Long organizationId, Long membershipId) {}
