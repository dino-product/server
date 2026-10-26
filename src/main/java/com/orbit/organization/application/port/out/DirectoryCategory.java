package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.MemberCategoryId;

/** 구성원에게 지정된 삭제되지 않은 직원·기사 유형. */
public record DirectoryCategory(MemberCategoryId id, String name, String color) {}
