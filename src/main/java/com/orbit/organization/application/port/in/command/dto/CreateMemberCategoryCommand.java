package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.MemberCategoryKind;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code color}는 프리셋 팔레트의 {@code #RRGGBB} 값이다. */
public record CreateMemberCategoryCommand(
        Long accountId, Long organizationId, MemberCategoryKind kind, String name, String color) {}
