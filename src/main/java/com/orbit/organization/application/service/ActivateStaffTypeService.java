package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.ActivateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.ActivateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;

@Service
public class ActivateStaffTypeService implements ActivateStaffTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;

    public ActivateStaffTypeService(
            OrganizationRepository organizations, MembershipRepository memberships, StaffTypeRepository staffTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
    }

    @Override
    @Transactional
    public StaffTypeInfo activate(ActivateStaffTypeCommand command) {
        var type = StaffTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                staffTypes);
        type.activate();
        staffTypes.save(type);
        return StaffTypeInfos.from(type);
    }
}
