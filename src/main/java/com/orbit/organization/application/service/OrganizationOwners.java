package com.orbit.organization.application.service;

import java.util.Objects;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 총관리자만 하는 발주사 유즈케이스의 요청자 확인. 발주사 조회와 입력 검증보다 먼저 호출해, 활성 소속이 아닌 요청에 발주사 존재 여부나 입력 오류가 드러나지 않게
 * 한다. 확인 순서는 organization 지침의 오류 순서를 따른다.
 */
final class OrganizationOwners {

    private OrganizationOwners() {}

    /** 요청자가 그 발주사의 활성 총관리자임을 확인하고 발주사 식별자를 돌려준다. */
    static OrganizationId require(MembershipRepository membershipRepository, Long accountId, Long organizationIdValue) {
        AccountId requester = new AccountId(Objects.requireNonNull(accountId, "accountId must not be null"));
        OrganizationId organizationId = organizationId(organizationIdValue);
        Membership membership = membershipRepository
                .findActive(organizationId, requester)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.NOT_ORGANIZATION_MEMBER));
        if (!membership.isOwner()) {
            throw new BusinessException(OrganizationErrorCode.OWNER_ONLY);
        }
        return organizationId;
    }

    private static OrganizationId organizationId(Long value) {
        try {
            return new OrganizationId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }
}
