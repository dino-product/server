package com.orbit.organization.domain;

/** 유형 종류. 직원 유형은 직원 소속에, 기사 유형은 기사 계약에만 지정하며 저장소도 따로 둔다. 작업 유형은 다른 규칙을 가져 여기에 넣지 않는다. */
public enum MemberCategoryKind {
    STAFF,
    TECHNICIAN
}
