package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.in.query.dto.GetCompanyCodeQuery;

public interface GetCompanyCodeUseCase {
    CompanyCodeInfo get(GetCompanyCodeQuery query);
}
