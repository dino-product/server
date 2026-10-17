package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;

/** 회사 코드 변경으로 폐기된 코드. 예전 링크·QR이 다른 발주사로 이어지지 않도록 어느 발주사에도 다시 발급하지 않는다. */
public record RetiredCompanyCode(OrganizationId organizationId, CompanyCode code, Instant retiredAt) {

    public RetiredCompanyCode {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(retiredAt, "retiredAt must not be null");
    }
}
