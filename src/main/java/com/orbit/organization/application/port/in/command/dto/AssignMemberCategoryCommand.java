package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.MemberCategoryKind;

/**
 * {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code kind}가 대상과 유형의 종류를 함께 정한다: 직원이면 {@code memberId}는 직원 소속, 기사면
 * 기사 계약 식별자다. {@code categoryId}가 {@code null}이면 "유형 없음"으로 바꾼다.
 */
public record AssignMemberCategoryCommand(
        Long accountId, Long organizationId, MemberCategoryKind kind, Long memberId, Long categoryId) {}
