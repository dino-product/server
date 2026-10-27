package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.GetWorkTypeDeletionImpactQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeDeletionImpactInfo;

public interface GetWorkTypeDeletionImpactUseCase {

    WorkTypeDeletionImpactInfo get(GetWorkTypeDeletionImpactQuery query);
}
