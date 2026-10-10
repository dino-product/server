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
 * 발주사 유즈케이스의 요청자 확인. 발주사 조회와 입력 검증보다 먼저 호출해, 활성 소속이 아닌 요청에 발주사 존재 여부나 입력 오류가 드러나지 않게
 * 한다. 확인 순서는 organization 지침의 오류 순서를 따른다.
 */
final class OrganizationOwners {

    private OrganizationOwners() {}

    /** 요청자가 그 발주사의 활성 총관리자임을 확인하고 발주사 식별자를 돌려준다. */
    static OrganizationId require(MembershipRepository membershipRepository, Long accountId, Long organizationIdValue) {
        AccountId requester = requester(accountId);
        OrganizationId organizationId = organizationId(organizationIdValue);
        if (!activeMembership(membershipRepository, requester, organizationId).isOwner()) {
            throw new BusinessException(OrganizationErrorCode.OWNER_ONLY);
        }
        return organizationId;
    }

    /**
     * 요청자가 그 발주사의 활성 직원 소속(총관리자 포함)임을 확인하고 발주사 식별자를 돌려준다. 기사는 직원 소속이 없어 거부된다. 총관리자 확인만 빠지고 순서는
     * {@link #require}와 같다.
     */
    static OrganizationId requireMember(
            MembershipRepository membershipRepository, Long accountId, Long organizationIdValue) {
        AccountId requester = requester(accountId);
        OrganizationId organizationId = organizationId(organizationIdValue);
        activeMembership(membershipRepository, requester, organizationId);
        return organizationId;
    }

    private static AccountId requester(Long accountId) {
        return new AccountId(Objects.requireNonNull(accountId, "accountId must not be null"));
    }

    private static Membership activeMembership(
            MembershipRepository membershipRepository, AccountId requester, OrganizationId organizationId) {
        return membershipRepository
                .findActive(organizationId, requester)
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.NOT_ORGANIZATION_MEMBER));
    }

    private static OrganizationId organizationId(Long value) {
        try {
            return new OrganizationId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }
}
