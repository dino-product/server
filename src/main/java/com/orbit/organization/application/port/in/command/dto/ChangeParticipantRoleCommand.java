package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.ParticipationType;

/**
 * {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code type}은 대상의 지금 역할이며 바뀐 뒤 역할은 그 반대다. {@code type}이 직원이면
 * {@code memberId}는 직원 소속, 기사면 기사 계약 식별자다. {@code categoryId}는 새 역할의 세부 유형이며 {@code null}이면 "유형 없음"이다.
 */
public record ChangeParticipantRoleCommand(
        Long accountId, Long organizationId, ParticipationType type, Long memberId, Long categoryId) {}
