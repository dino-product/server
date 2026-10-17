package com.orbit.organization.adapter.in.web;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberCategoriesResponse(
        @Schema(description = "삭제되지 않은 유형. 만든 순서대로") List<MemberCategoryResponse> categories) {

    static MemberCategoriesResponse from(List<MemberCategoryInfo> categories) {
        return new MemberCategoriesResponse(
                categories.stream().map(MemberCategoryResponse::from).toList());
    }
}
