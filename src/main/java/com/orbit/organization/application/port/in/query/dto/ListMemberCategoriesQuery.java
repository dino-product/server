package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.MemberCategoryKind;

/** {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. */
public record ListMemberCategoriesQuery(Long accountId, Long organizationId, MemberCategoryKind kind) {}
