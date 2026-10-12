package com.orbit.organization.adapter.out.persistence;

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
import com.orbit.shared.persistence.BaseTimeEntity;

@Entity
@Table(name = "companies")
class OrganizationJpaEntity extends BaseTimeEntity {

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

    protected OrganizationJpaEntity() {}

    private OrganizationJpaEntity(String code, String name, Industry industry) {
        this.code = code;
        this.name = name;
        this.industry = industry;
    }

    static OrganizationJpaEntity from(Organization organization) {
        if (organization.id().isPresent()) {
            throw new IllegalArgumentException("only new organizations can be saved");
        }
        return new OrganizationJpaEntity(
                organization.code().value(), organization.name().value(), organization.industry());
    }

    /** 저장된 발주사가 바꿀 수 있는 값(발주사명·업종·회사 코드)을 옮긴다. 식별자는 그대로 두고 수정 시각은 감사 컬럼이 채운다. */
    void update(Organization organization) {
        this.code = organization.code().value();
        this.name = organization.name().value();
        this.industry = organization.industry();
    }

    Organization toDomain() {
        return Organization.reconstitute(
                new OrganizationId(id), new OrganizationName(name), industry, new CompanyCode(code));
    }
}
