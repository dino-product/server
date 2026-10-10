package com.orbit.organization.domain;

/** 유형 식별자. 직원 유형과 기사 유형은 저장소가 달라 같은 값이 겹칠 수 있으므로 항상 {@link MemberCategoryKind}와 함께 쓴다. */
public record MemberCategoryId(Long value) {

    public MemberCategoryId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("memberCategoryId must be positive");
        }
    }
}
