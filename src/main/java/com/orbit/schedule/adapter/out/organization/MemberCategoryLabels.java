package com.orbit.schedule.adapter.out.organization;

import com.orbit.organization.MemberCategoryLabel;
import com.orbit.schedule.application.port.out.TypeLabel;

/** organization의 직원·기사 유형 표시 정보를 schedule의 유형 표시 정보로 옮긴다. 삭제된 유형은 organization이 담지 않으므로 삭제 표시는 늘 거짓이다. */
final class MemberCategoryLabels {

    private MemberCategoryLabels() {}

    /** 유형이 없으면 null. */
    static TypeLabel toTypeLabel(MemberCategoryLabel category) {
        return category == null ? null : new TypeLabel(category.categoryId(), category.name(), category.color(), false);
    }
}
