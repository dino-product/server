package com.orbit.organization.application.port.in.command;

import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;

public interface ChangeCompanyCodeUseCase {
    CompanyCodeInfo change(ChangeCompanyCodeCommand command);
}
