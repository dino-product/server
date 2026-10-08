package com.orbit.organization.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.UpdateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationCommand;
import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;

/**
 * 발주사명·업종 수정. 입력은 생성과 같은 규칙으로 검사하고, 다른 발주사와 같은 이름이나 현재 값과 같은 값도 그대로 저장한다. 동시에 수정하면 마지막 요청이
 * 이긴다(발주사에 버전 컬럼이 없음).
 */
@Service
public class UpdateOrganizationService implements UpdateOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final Clock clock;

    public UpdateOrganizationService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrganizationInfo update(UpdateOrganizationCommand command) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        OrganizationName name = OrganizationInputs.name(command.name());
        Industry industry = OrganizationInputs.industry(command.industry());

        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("active membership must belong to an organization"));
        organization.changeInfo(name, industry, clock.instant());

        return OrganizationInfo.from(organizationRepository.save(organization));
    }
}
