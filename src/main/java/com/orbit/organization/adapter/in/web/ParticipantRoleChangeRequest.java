package com.orbit.organization.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 역할 변경 본문. 경로가 직원 소속이면 기사 유형, 기사 계약이면 직원 유형의 식별자다(바뀐 뒤 역할의 유형). */
public record ParticipantRoleChangeRequest(
        @Schema(description = "바뀐 뒤 역할의 세부 유형 식별자. null이면 \"유형 없음\"", example = "1", nullable = true)
        Long categoryId) {}
