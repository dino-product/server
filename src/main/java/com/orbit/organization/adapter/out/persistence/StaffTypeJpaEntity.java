package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.StaffTypeId;
import com.orbit.organization.domain.TypeColor;

@Entity
@Table(
        name = "staff_type",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_staff_type_organization_name",
                        columnNames = {"organization_id", "name"}))
class StaffTypeJpaEntity {
    @Id
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 10)
    private String name;

    @Column(nullable = false)
    private Short color;

    @Column(nullable = false)
    private boolean active;

    protected StaffTypeJpaEntity() {}

    private StaffTypeJpaEntity(StaffType type) {
        id = type.id().value();
        organizationId = type.organizationId().value();
        updateFrom(type);
    }

    static StaffTypeJpaEntity from(StaffType type) {
        return new StaffTypeJpaEntity(type);
    }

    void updateFrom(StaffType type) {
        name = type.name().value();
        color = (short) type.color().value();
        active = type.active();
    }

    StaffType toDomain() {
        return StaffType.reconstitute(
                new StaffTypeId(id),
                new OrganizationId(organizationId),
                new PersonnelTypeName(name),
                new TypeColor(color),
                active);
    }
}
