package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryKind;

@Entity
@Table(name = "company_membership_category")
class StaffCategoryJpaEntity extends MemberCategoryJpaEntity {

    /** 삭제되지 않은 행끼리 (발주사, 이름)이 유일한 부분 유일 인덱스. 정의는 {@code db/migration/V1__init.sql}에 있다. */
    static final String UNDELETED_NAME_INDEX = "uk_company_membership_category_undeleted_name";

    protected StaffCategoryJpaEntity() {}

    StaffCategoryJpaEntity(MemberCategory category) {
        super(category);
    }

    @Override
    MemberCategoryKind kind() {
        return MemberCategoryKind.STAFF;
    }
}
