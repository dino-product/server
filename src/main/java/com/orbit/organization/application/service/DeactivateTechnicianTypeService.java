package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.DeactivateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.DeactivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;

@Service
public class DeactivateTechnicianTypeService implements DeactivateTechnicianTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;

    public DeactivateTechnicianTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
    }

    @Override
    @Transactional
    public TechnicianTypeInfo deactivate(DeactivateTechnicianTypeCommand command) {
        var type = TechnicianTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                technicianTypes);
        type.deactivate();
        technicianTypes.save(type);
        return TechnicianTypeInfos.from(type);
    }
}
