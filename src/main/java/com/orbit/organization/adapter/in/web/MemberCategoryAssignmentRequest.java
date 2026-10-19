package com.orbit.organization.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 세부 유형 지정 본문. 경로가 직원 소속이면 직원 유형, 기사 계약이면 기사 유형의 식별자다. */
public record MemberCategoryAssignmentRequest(
        @Schema(description = "지정할 유형 식별자. null이면 \"유형 없음\"으로 바꾼다", example = "1", nullable = true)
        Long categoryId) {}
