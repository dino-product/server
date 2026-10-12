package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.Organization;

/** 발주사의 현재 회사 코드. 참여 링크·QR은 클라이언트가 이 코드로 만든다. */
public record CompanyCodeInfo(String companyCode) {

    public static CompanyCodeInfo from(Organization organization) {
        return new CompanyCodeInfo(organization.code().value());
    }
}
