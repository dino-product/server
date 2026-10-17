package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.UpdateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;

public interface UpdateMemberCategoryUseCase {

    MemberCategoryInfo update(UpdateMemberCategoryCommand command);
}
