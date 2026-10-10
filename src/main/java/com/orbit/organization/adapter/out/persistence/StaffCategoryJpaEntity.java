package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryKind;

@Entity
@Table(name = "company_membership_category")
class StaffCategoryJpaEntity extends MemberCategoryJpaEntity {

    protected StaffCategoryJpaEntity() {}

    StaffCategoryJpaEntity(MemberCategory category) {
        super(category);
    }

    @Override
    MemberCategoryKind kind() {
        return MemberCategoryKind.STAFF;
    }
}
