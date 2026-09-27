package com.orbit.organization.domain;

public final class StaffType {
    private final StaffTypeId id;
    private final OrganizationId organizationId;
    private PersonnelTypeName name;
    private TypeColor color;
    private boolean active;

    private StaffType(
            StaffTypeId id, OrganizationId organizationId, PersonnelTypeName name, TypeColor color, boolean active) {
        if (id == null || organizationId == null || name == null || color == null) {
            throw new OrganizationRuleViolation("type fields must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.color = color;
        this.active = active;
    }

    public static StaffType create(
            StaffTypeId id, OrganizationId organizationId, PersonnelTypeName name, TypeColor color) {
        return new StaffType(id, organizationId, name, color, true);
    }

    /** 저장된 활성 상태를 보존하며 신규 생성의 기본 활성 상태를 적용하지 않는다. */
    public static StaffType reconstitute(
            StaffTypeId id, OrganizationId organizationId, PersonnelTypeName name, TypeColor color, boolean active) {
        return new StaffType(id, organizationId, name, color, active);
    }

    public void rename(PersonnelTypeName name) {
        if (name == null) {
            throw new OrganizationRuleViolation("name must not be null");
        }
        this.name = name;
    }

    public void changeColor(TypeColor color) {
        if (color == null) {
            throw new OrganizationRuleViolation("color must not be null");
        }
        this.color = color;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public StaffTypeId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PersonnelTypeName name() {
        return name;
    }

    public TypeColor color() {
        return color;
    }

    public boolean active() {
        return active;
    }
}
