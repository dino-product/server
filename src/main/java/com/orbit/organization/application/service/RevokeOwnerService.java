package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.RevokeOwnerUseCase;
import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.LastOwnerException;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * 총관리자가 본인 또는 다른 총관리자의 총관리자 표시를 뗀다. 확인 순서는 {@link OrganizationOwners}를 따르고, 마지막 총관리자면 409(ORGANIZATION-005)다.
 * 총관리자 수는 발주사 잠금을 잡은 뒤 세므로, 남은 두 총관리자가 서로를 동시에 해제해도 뒤 요청은 앞 요청의 결과를 보고 거부된다. 총관리자가 아닌 소속을 해제하면
 * 저장하지 않고 성공한다.
 */
@Service
public class RevokeOwnerService implements RevokeOwnerUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public RevokeOwnerService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public void revoke(RevokeOwnerCommand command) {
        OrganizationId organizationId = new OrganizationId(command.organizationId());
        OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
        Membership target = OrganizationOwners.requireMembershipOf(
                membershipRepository, organizationId, new MembershipId(command.membershipId()));
        if (!target.isOwner()) {
            return;
        }
        membershipRepository.save(revoked(target, membershipRepository.countActiveOwners(organizationId)));
    }

    private static Membership revoked(Membership target, long activeOwners) {
        try {
            return target.revokeOwner(activeOwners);
        } catch (LastOwnerException exception) {
            throw new BusinessException(OrganizationErrorCode.LAST_OWNER, exception);
        }
    }
}
