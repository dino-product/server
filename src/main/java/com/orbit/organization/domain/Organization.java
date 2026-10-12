package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 발주사. 총관리자는 직원 소속의 표시로 나타내므로 발주사는 소속을 가리키지 않는다. */
public final class Organization {

    private final OrganizationId id;
    private final OrganizationName name;
    private final Industry industry;
    private final CompanyCode code;
    private final Instant createdAt;

    private Organization(
            OrganizationId id, OrganizationName name, Industry industry, CompanyCode code, Instant createdAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.industry = Objects.requireNonNull(industry, "industry must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Organization create(OrganizationName name, Industry industry, CompanyCode code, Instant createdAt) {
        return new Organization(null, name, industry, code, createdAt);
    }

    public static Organization reconstitute(
            OrganizationId id, OrganizationName name, Industry industry, CompanyCode code, Instant createdAt) {
        return new Organization(Objects.requireNonNull(id, "id must not be null"), name, industry, code, createdAt);
    }

    public Optional<OrganizationId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationName name() {
        return name;
    }

    public Industry industry() {
        return industry;
    }

    public CompanyCode code() {
        return code;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
