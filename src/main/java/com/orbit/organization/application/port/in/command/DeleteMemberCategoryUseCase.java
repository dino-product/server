package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.DeleteMemberCategoryCommand;

public interface DeleteMemberCategoryUseCase {

    void delete(DeleteMemberCategoryCommand command);
}
