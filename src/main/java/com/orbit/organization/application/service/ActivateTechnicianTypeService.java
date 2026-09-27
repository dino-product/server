package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.ActivateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.ActivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;

@Service
public class ActivateTechnicianTypeService implements ActivateTechnicianTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;

    public ActivateTechnicianTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
    }

    @Override
    @Transactional
    public TechnicianTypeInfo activate(ActivateTechnicianTypeCommand command) {
        var type = TechnicianTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                technicianTypes);
        type.activate();
        technicianTypes.save(type);
        return TechnicianTypeInfos.from(type);
    }
}
