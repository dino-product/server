package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CreateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TypeColor;
import com.orbit.shared.error.BusinessException;

@Service
public class CreateTechnicianTypeService implements CreateTechnicianTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;

    public CreateTechnicianTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
    }

    @Override
    @Transactional
    public TechnicianTypeInfo create(CreateTechnicianTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        var name = OrganizationRuleViolations.call(() -> new PersonnelTypeName(command.name()));
        var color = OrganizationRuleViolations.call(() -> new TypeColor(command.color()));
        if (technicianTypes.existsByOrganizationAndName(organizationId, name, null)) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME);
        }
        var typeId = technicianTypes.nextId();
        var type = OrganizationRuleViolations.call(() -> TechnicianType.create(typeId, organizationId, name, color));
        try {
            technicianTypes.save(type);
        } catch (PersonnelTypeNameConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME, conflict);
        }
        return TechnicianTypeInfos.from(type);
    }
}
