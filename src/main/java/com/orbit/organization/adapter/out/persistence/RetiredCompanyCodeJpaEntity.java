package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.RetiredCompanyCode;

/** 폐기된 회사 코드. 코드는 모든 폐기 코드 사이에서 유일하며, 현재 코드와의 중복은 발급 전 확인이 막는다. */
@Entity
@Table(name = "retired_company_codes")
class RetiredCompanyCodeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long organizationId;

    @Column(name = "code", nullable = false, unique = true, length = CompanyCode.LENGTH)
    private String code;

    @Column(name = "retired_at", nullable = false)
    private Instant retiredAt;

    protected RetiredCompanyCodeJpaEntity() {}

    private RetiredCompanyCodeJpaEntity(RetiredCompanyCode retiredCode) {
        this.organizationId = retiredCode.organizationId().value();
        this.code = retiredCode.code().value();
        this.retiredAt = retiredCode.retiredAt();
    }

    static RetiredCompanyCodeJpaEntity from(RetiredCompanyCode retiredCode) {
        return new RetiredCompanyCodeJpaEntity(retiredCode);
    }

    RetiredCompanyCode toDomain() {
        return new RetiredCompanyCode(new OrganizationId(organizationId), new CompanyCode(code), retiredAt);
    }
}
