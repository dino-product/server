package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;

public interface CreateOrganizationUseCase {
    CreatedOrganizationInfo create(CreateOrganizationCommand command);
}
