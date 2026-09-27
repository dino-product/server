package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationDetailsCommand;
import com.orbit.organization.application.port.in.query.dto.OrganizationDetailsInfo;

public interface UpdateOrganizationDetailsUseCase {
    OrganizationDetailsInfo update(UpdateOrganizationDetailsCommand command);
}
