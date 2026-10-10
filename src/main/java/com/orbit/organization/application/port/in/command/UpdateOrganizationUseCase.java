package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationCommand;
import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;

public interface UpdateOrganizationUseCase {

    OrganizationInfo update(UpdateOrganizationCommand command);
}
