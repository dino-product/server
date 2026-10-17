package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberCategoryResponse(
        @Schema(description = "유형 식별자. 직원 유형과 기사 유형은 따로 매긴다", example = "1")
        Long categoryId,

        @Schema(description = "유형 이름", example = "전기 기사") String name,

        @Schema(description = "색상(대문자 #RRGGBB)", example = "#3B82F6")
        String color) {

    static MemberCategoryResponse from(MemberCategoryInfo category) {
        return new MemberCategoryResponse(category.categoryId(), category.name(), category.color());
    }
}
