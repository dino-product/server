package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TechnicianTypeId;
import com.orbit.organization.domain.TypeColor;

@Entity
@Table(
        name = "technician_type",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_technician_type_organization_name",
                        columnNames = {"organization_id", "name"}))
class TechnicianTypeJpaEntity {
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

    protected TechnicianTypeJpaEntity() {}

    private TechnicianTypeJpaEntity(TechnicianType type) {
        id = type.id().value();
        organizationId = type.organizationId().value();
        updateFrom(type);
    }

    static TechnicianTypeJpaEntity from(TechnicianType type) {
        return new TechnicianTypeJpaEntity(type);
    }

    void updateFrom(TechnicianType type) {
        name = type.name().value();
        color = (short) type.color().value();
        active = type.active();
    }

    TechnicianType toDomain() {
        return TechnicianType.reconstitute(
                new TechnicianTypeId(id),
                new OrganizationId(organizationId),
                new PersonnelTypeName(name),
                new TypeColor(color),
                active);
    }
}
