package com.orbit.organization.application.service;

import java.util.Optional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.Organization;
import com.orbit.shared.error.BusinessException;

/** 총관리자만 회사 정보를 읽거나 바꾸며 회사 부재와 권한 부재를 같은 오류로 반환한다. */
final class OrganizationOwners {
    private OrganizationOwners() {}

    static Organization requireOwner(
            Optional<Organization> candidate, MembershipRepository memberships, AuthAccountId accountId) {
        var organization = candidate.orElseThrow(OrganizationOwners::notOwner);
        var membership = memberships
                .findByOrganizationAndAccount(organization.id(), accountId)
                .orElseThrow(OrganizationOwners::notOwner);
        if (!organization.isManagedBy(membership)) {
            throw notOwner();
        }
        return organization;
    }

    private static BusinessException notOwner() {
        return new BusinessException(OrganizationErrorCode.NOT_ORGANIZATION_OWNER);
    }
}
