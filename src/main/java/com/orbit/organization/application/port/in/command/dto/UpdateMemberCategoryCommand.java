package com.orbit.organization.application.port.in.command.dto;

import com.orbit.organization.domain.MemberCategoryKind;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. 이름·색상을 함께 교체한다. */
public record UpdateMemberCategoryCommand(
        Long accountId, Long organizationId, MemberCategoryKind kind, Long categoryId, String name, String color) {}
