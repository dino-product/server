package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.AssignMemberCategoryCommand;

public interface AssignMemberCategoryUseCase {

    void assign(AssignMemberCategoryCommand command);
}
