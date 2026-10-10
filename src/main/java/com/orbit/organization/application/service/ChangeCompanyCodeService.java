package com.orbit.organization.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.ChangeCompanyCodeUseCase;
import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

/**
 * 회사 코드 재발급. 이전 코드는 폐기 코드로 남기고, 소속·참여 요청은 회사 식별자로 이어지므로 그대로 둔다. 발주사 행을 잠그고 읽어 동시에 바꾸는 요청은 차례로
 * 처리된다(뒤 요청은 앞 요청이 발급한 코드를 폐기한다).
 */
@Service
public class ChangeCompanyCodeService implements ChangeCompanyCodeUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final CompanyCodeGenerator codeGenerator;
    private final Clock clock;

    public ChangeCompanyCodeService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            CompanyCodeGenerator codeGenerator,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CompanyCodeInfo change(ChangeCompanyCodeCommand command) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        Organization organization = organizationRepository
                .findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalStateException("active membership must belong to an organization"));

        organizationRepository.saveRetiredCode(organization.changeCode(
                CompanyCodeIssuer.issue(organizationRepository, codeGenerator), clock.instant()));

        return CompanyCodeInfo.from(organizationRepository.save(organization));
    }
}
