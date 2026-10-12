package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;

@Entity
@Table(name = "companies")
class OrganizationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = CompanyCode.LENGTH)
    private String code;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "industry_code", nullable = false, length = 32)
    private Industry industry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationJpaEntity() {}

    private OrganizationJpaEntity(String code, String name, Industry industry, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.industry = industry;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    static OrganizationJpaEntity from(Organization organization) {
        if (organization.id().isPresent()) {
            throw new IllegalArgumentException("only new organizations can be saved");
        }
        return new OrganizationJpaEntity(
                organization.code().value(),
                organization.name().value(),
                organization.industry(),
                organization.createdAt());
    }

    Organization toDomain() {
        return Organization.reconstitute(
                new OrganizationId(id), new OrganizationName(name), industry, new CompanyCode(code), createdAt);
    }
}
