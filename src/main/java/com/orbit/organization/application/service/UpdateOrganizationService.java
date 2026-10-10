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
 * 발주사명·업종 수정. 입력은 생성과 같은 규칙으로 검사하고, 다른 발주사와 같은 이름이나 현재 값과 같은 값도 그대로 저장한다. 저장은 발주사 행 전체를
 * 다시 쓰므로 회사 코드 변경과 같은 행 잠금으로 읽어, 그 사이 바뀐 회사 코드를 되돌리지 않는다. 정보 수정끼리는 차례로 처리되어 마지막 요청이 이긴다.
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
                .findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalStateException("active membership must belong to an organization"));
        organization.changeInfo(name, industry, clock.instant());

        return OrganizationInfo.from(organizationRepository.save(organization));
    }
}
