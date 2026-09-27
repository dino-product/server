package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.DeleteStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.DeleteStaffTypeCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeInUseException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.application.port.out.StaffTypeUsagePort;
import com.orbit.shared.error.BusinessException;

@Service
public class DeleteStaffTypeService implements DeleteStaffTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;
    private final StaffTypeUsagePort usage;

    public DeleteStaffTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            StaffTypeRepository staffTypes,
            StaffTypeUsagePort usage) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
        this.usage = usage;
    }

    @Override
    @Transactional
    public void delete(DeleteStaffTypeCommand command) {
        var type = StaffTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                staffTypes);
        if (usage.isAssigned(type.id())) {
            throw new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_IN_USE);
        }
        try {
            staffTypes.delete(type.id());
        } catch (PersonnelTypeInUseException inUse) {
            throw new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_IN_USE, inUse);
        }
    }
}
