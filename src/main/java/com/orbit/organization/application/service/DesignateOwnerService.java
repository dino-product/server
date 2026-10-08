package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.DesignateOwnerUseCase;
import com.orbit.organization.application.port.in.command.dto.DesignateOwnerCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;

/**
 * 총관리자가 같은 발주사의 직원 소속을 총관리자로 추가 지정한다. 확인 순서는 {@link OrganizationOwners}를 따른다. 이미 총관리자인 소속을 다시 지정하면 저장하지 않고
 * 성공한다(결과가 요청과 같으므로 중복·재전송 요청을 실패로 돌려주지 않는다).
 */
@Service
public class DesignateOwnerService implements DesignateOwnerUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public DesignateOwnerService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public void designate(DesignateOwnerCommand command) {
        OrganizationId organizationId = new OrganizationId(command.organizationId());
        OrganizationOwners.lockAndRequireOwner(
                organizationRepository, membershipRepository, command.accountId(), organizationId);
        Membership target = OrganizationOwners.requireMembershipOf(
                membershipRepository, organizationId, new MembershipId(command.membershipId()));
        if (!target.isOwner()) {
            membershipRepository.save(target.designateAsOwner());
        }
    }
}
