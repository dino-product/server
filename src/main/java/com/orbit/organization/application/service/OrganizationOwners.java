package com.orbit.organization.application.service;

import java.util.Objects;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 발주사 유즈케이스의 요청자 확인.
 *
 * <p>발주사 정보 조회·수정과 유형 관리는 {@link #require}·{@link #requireMember}를 발주사 조회와 입력 검증보다 먼저 호출해, 활성 소속이 아닌 요청에
 * 발주사 존재 여부나 입력 오류가 드러나지 않게 한다. 확인 순서는 organization 지침의 오류 순서를 따른다.
 *
 * <p>총관리자 지정·해제는 {@link #lockAndRequireOwner}와 {@link #requireMembershipOf}로 다음 순서를 따른다.
 *
 * <ol>
 *   <li>요청자가 그 발주사의 활성 직원 소속이 아니면 403(ORGANIZATION-002). 잠금을 기다리기 전에 확인해, 소속이 아닌 요청이 대기 한도 초과(409)로
 *       발주사 존재를 알아내지 못하게 한다.
 *   <li>발주사 행을 잠근다. 총관리자 확인보다 먼저 잡아야, 서로를 동시에 해제하거나 해제된 총관리자가 동시에 지정하는 요청이 앞 요청의 결과를 보고 판단한다.
 *       대기 한도를 넘기면 409(ORGANIZATION-010).
 *   <li>잠근 뒤 요청자 소속을 다시 읽어, 그 사이 활성 소속이 아니게 되었으면 403(ORGANIZATION-002), 총관리자가 아니면 403(ORGANIZATION-003).
 *   <li>대상 소속이 없거나 다른 발주사 소속이면 존재를 숨기고 404(ORGANIZATION-004). 기사 계약은 직원 소속과 식별자가 달라 여기서 찾지 못한다.
 *   <li>대상 소속의 상태·총관리자 수 같은 유즈케이스별 규칙(409)은 각 서비스가 확인한다.
 * </ol>
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

    static Membership lockAndRequireOwner(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            Long accountId,
            OrganizationId organizationId) {
        AccountId requester = requester(accountId);
        // 잠그기 전에는 소속을 읽어 들이지 않는다. 읽으면 잠근 뒤의 조회가 앞 요청이 커밋하기 전 값을 그대로 돌려준다.
        if (!membershipRepository.existsActive(organizationId, requester)) {
            throw new BusinessException(OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        }
        OrganizationLocks.lock(organizationRepository, organizationId);
        Membership membership = activeMembership(membershipRepository, requester, organizationId);
        if (!membership.isOwner()) {
            throw new BusinessException(OrganizationErrorCode.OWNER_ONLY);
        }
        return membership;
    }

    static Membership requireMembershipOf(
            MembershipRepository membershipRepository, OrganizationId organizationId, MembershipId membershipId) {
        return membershipRepository
                .findById(membershipId)
                .filter(membership -> membership.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.MEMBERSHIP_NOT_FOUND));
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
