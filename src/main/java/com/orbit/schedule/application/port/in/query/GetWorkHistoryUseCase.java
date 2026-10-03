package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryInfo;

public interface GetWorkHistoryUseCase {

    WorkHistoryInfo get(GetWorkHistoryQuery query);
}
