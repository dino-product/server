package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.ChangeCompanyCodeUseCase;
import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.out.CompanyCodeConflictException;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

@Service
public class ChangeCompanyCodeService implements ChangeCompanyCodeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final CompanyCodeGenerator codes;

    public ChangeCompanyCodeService(
            OrganizationRepository organizations, MembershipRepository memberships, CompanyCodeGenerator codes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.codes = codes;
    }

    @Override
    @Transactional
    public CompanyCodeInfo change(ChangeCompanyCodeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        var organization = OrganizationOwners.requireOwner(
                organizations.findByIdForUpdate(organizationId), memberships, accountId);
        var code = CompanyCodes.nextAvailableCode(codes, organizations);
        organization.changeCode(code);

        try {
            organizations.update(organization);
            organizations.flush();
        } catch (CompanyCodeConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.COMPANY_CODE_CONFLICT, conflict);
        }
        return new CompanyCodeInfo(organization.id().value(), code.value());
    }
}
