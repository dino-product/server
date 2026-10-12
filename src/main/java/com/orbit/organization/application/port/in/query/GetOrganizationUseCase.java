package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.GetOrganizationQuery;
import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;

public interface GetOrganizationUseCase {

    OrganizationInfo get(GetOrganizationQuery query);
}
