package com.orbit.organization.application.service;

import java.util.Objects;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.StaffTypeId;
import com.orbit.shared.error.BusinessException;

/** 상태 변경과 삭제의 총관리자·회사 범위 확인 순서를 공유한다. */
final class StaffTypeTargets {
    private StaffTypeTargets() {}

    static StaffType require(
            Long rawAccountId,
            Long rawOrganizationId,
            Long rawTypeId,
            OrganizationRepository organizations,
            MembershipRepository memberships,
            StaffTypeRepository staffTypes) {
        Objects.requireNonNull(rawAccountId, "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(rawAccountId));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(rawOrganizationId));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        var typeId = OrganizationRuleViolations.call(() -> new StaffTypeId(rawTypeId));
        return staffTypes
                .findByIdForUpdate(typeId)
                .filter(type -> type.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND));
    }
}
