package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.DeactivateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.DeactivateStaffTypeCommand;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;

@Service
public class DeactivateStaffTypeService implements DeactivateStaffTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final StaffTypeRepository staffTypes;

    public DeactivateStaffTypeService(
            OrganizationRepository organizations, MembershipRepository memberships, StaffTypeRepository staffTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.staffTypes = staffTypes;
    }

    @Override
    @Transactional
    public StaffTypeInfo deactivate(DeactivateStaffTypeCommand command) {
        var type = StaffTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                staffTypes);
        type.deactivate();
        staffTypes.save(type);
        return StaffTypeInfos.from(type);
    }
}
