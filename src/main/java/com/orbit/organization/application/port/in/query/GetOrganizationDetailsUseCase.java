package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.GetOrganizationDetailsQuery;
import com.orbit.organization.application.port.in.query.dto.OrganizationDetailsInfo;

public interface GetOrganizationDetailsUseCase {
    OrganizationDetailsInfo get(GetOrganizationDetailsQuery query);
}
