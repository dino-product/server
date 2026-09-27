package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.DeleteTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.DeleteTechnicianTypeCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeInUseException;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.application.port.out.TechnicianTypeUsagePort;
import com.orbit.shared.error.BusinessException;

@Service
public class DeleteTechnicianTypeService implements DeleteTechnicianTypeUseCase {
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final TechnicianTypeRepository technicianTypes;
    private final TechnicianTypeUsagePort usage;

    public DeleteTechnicianTypeService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            TechnicianTypeRepository technicianTypes,
            TechnicianTypeUsagePort usage) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.technicianTypes = technicianTypes;
        this.usage = usage;
    }

    @Override
    @Transactional
    public void delete(DeleteTechnicianTypeCommand command) {
        var type = TechnicianTypeTargets.require(
                command.accountId(),
                command.organizationId(),
                command.typeId(),
                organizations,
                memberships,
                technicianTypes);
        if (usage.isAssigned(type.id())) {
            throw new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_IN_USE);
        }
        try {
            technicianTypes.delete(type.id());
        } catch (PersonnelTypeInUseException inUse) {
            throw new BusinessException(OrganizationErrorCode.PERSONNEL_TYPE_IN_USE, inUse);
        }
    }
}
