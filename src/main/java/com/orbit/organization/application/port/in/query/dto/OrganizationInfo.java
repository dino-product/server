package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Organization;

/** 조직 설정의 발주사 정보. 회사 코드는 별도 유즈케이스가 다룬다. */
public record OrganizationInfo(Long organizationId, String name, Industry industry) {

    public static OrganizationInfo from(Organization organization) {
        return new OrganizationInfo(
                organization
                        .id()
                        .orElseThrow(() -> new IllegalArgumentException("organization must have an id"))
                        .value(),
                organization.name().value(),
                organization.industry());
    }
}
