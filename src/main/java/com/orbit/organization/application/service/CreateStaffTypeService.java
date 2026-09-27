package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CreateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.TypeColor;
import com.orbit.shared.error.BusinessException;

@Service
public class CreateStaffTypeService implements CreateStaffTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;

    public CreateStaffTypeService(
            OrganizationRepository organizations, MembershipRepository memberships, StaffTypeRepository staffTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
    }

    @Override
    @Transactional
    public StaffTypeInfo create(CreateStaffTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        var accountId = OrganizationRuleViolations.call(() -> new AuthAccountId(command.accountId()));
        var organizationId = OrganizationRuleViolations.call(() -> new OrganizationId(command.organizationId()));
        OrganizationOwners.requireOwner(organizations.findById(organizationId), memberships, accountId);
        var name = OrganizationRuleViolations.call(() -> new PersonnelTypeName(command.name()));
        var color = OrganizationRuleViolations.call(() -> new TypeColor(command.color()));
        if (staffTypes.existsByOrganizationAndName(organizationId, name, null)) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME);
        }
        var typeId = staffTypes.nextId();
        var type = OrganizationRuleViolations.call(() -> StaffType.create(typeId, organizationId, name, color));
        try {
            staffTypes.save(type);
        } catch (PersonnelTypeNameConflictException conflict) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME, conflict);
        }
        return StaffTypeInfos.from(type);
    }
}
