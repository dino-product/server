package com.orbit.organization.domain;

public final class TechnicianType {
    private final TechnicianTypeId id;
    private final OrganizationId organizationId;
    private PersonnelTypeName name;
    private TypeColor color;
    private boolean active = true;

    private TechnicianType(
            TechnicianTypeId id, OrganizationId organizationId, PersonnelTypeName name, TypeColor color) {
        if (id == null || organizationId == null || name == null || color == null) {
            throw new OrganizationRuleViolation("type fields must not be null");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.color = color;
    }

    public static TechnicianType create(
            TechnicianTypeId id, OrganizationId organizationId, PersonnelTypeName name, TypeColor color) {
        return new TechnicianType(id, organizationId, name, color);
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

    public TechnicianTypeId id() {
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
