package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.UpdateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffTypeId;
import com.orbit.organization.domain.TypeColor;
import com.orbit.shared.error.BusinessException;

@Service
public class UpdateStaffTypeService implements UpdateStaffTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;

    public UpdateStaffTypeService(
            OrganizationRepository organizations, MembershipRepository memberships, StaffTypeRepository staffTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
    }

    @Override
    @Transactional
    public StaffTypeInfo update(UpdateStaffTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        var typeId = OrganizationRuleViolations.call(() -> new StaffTypeId(command.typeId()));
        var type = staffTypes
                .findByIdForUpdate(typeId)
                .filter(candidate -> candidate.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND));
        var name = OrganizationRuleViolations.call(() -> new PersonnelTypeName(command.name()));
        var color = OrganizationRuleViolations.call(() -> new TypeColor(command.color()));
        if (staffTypes.existsByOrganizationAndName(organizationId, name, typeId)) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME);
        }
        OrganizationRuleViolations.call(() -> {
            type.rename(name);
            type.changeColor(color);
            return null;
        });
        try {
            staffTypes.save(type);
        } catch (PersonnelTypeNameConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME, conflict);
        }
        return StaffTypeInfos.from(type);
    }
}
