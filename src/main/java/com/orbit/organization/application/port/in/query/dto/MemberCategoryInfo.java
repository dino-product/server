package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryKind;

/** 삭제되지 않은 직원·기사 유형. 삭제된 유형은 조회 결과에 나오지 않는다. */
public record MemberCategoryInfo(Long categoryId, MemberCategoryKind kind, String name, String color) {

    public static MemberCategoryInfo from(MemberCategory category) {
        return new MemberCategoryInfo(
                category.id()
                        .orElseThrow(() -> new IllegalArgumentException("category must have an id"))
                        .value(),
                category.kind(),
                category.name().value(),
                category.color().value());
    }
}
