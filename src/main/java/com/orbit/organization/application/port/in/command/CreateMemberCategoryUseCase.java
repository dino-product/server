package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;

public interface CreateMemberCategoryUseCase {

    MemberCategoryInfo create(CreateMemberCategoryCommand command);
}
