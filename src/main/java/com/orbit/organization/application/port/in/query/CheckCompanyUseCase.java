package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.CheckCompanyQuery;
import com.orbit.organization.application.port.in.query.dto.CompanyCheckInfo;

public interface CheckCompanyUseCase {

    CompanyCheckInfo check(CheckCompanyQuery query);
}
