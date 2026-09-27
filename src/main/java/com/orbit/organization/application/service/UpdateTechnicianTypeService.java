package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.UpdateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianTypeId;
import com.orbit.organization.domain.TypeColor;
import com.orbit.shared.error.BusinessException;

@Service
public class UpdateTechnicianTypeService implements UpdateTechnicianTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;

    public UpdateTechnicianTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
    }

    @Override
    @Transactional
    public TechnicianTypeInfo update(UpdateTechnicianTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        var typeId = OrganizationRuleViolations.call(() -> new TechnicianTypeId(command.typeId()));
        var type = technicianTypes
                .findByIdForUpdate(typeId)
                .filter(candidate -> candidate.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND));
        var name = OrganizationRuleViolations.call(() -> new PersonnelTypeName(command.name()));
        var color = OrganizationRuleViolations.call(() -> new TypeColor(command.color()));
        if (technicianTypes.existsByOrganizationAndName(organizationId, name, typeId)) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME);
        }
        OrganizationRuleViolations.call(() -> {
            type.rename(name);
            type.changeColor(color);
            return null;
        });
        try {
            technicianTypes.save(type);
        } catch (PersonnelTypeNameConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME, conflict);
        }
        return TechnicianTypeInfos.from(type);
    }
}
