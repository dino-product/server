package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.CountWorksByTypeQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkTypeUsageInfo;

public interface CountWorksByTypeUseCase {

    WorkTypeUsageInfo count(CountWorksByTypeQuery query);
}
