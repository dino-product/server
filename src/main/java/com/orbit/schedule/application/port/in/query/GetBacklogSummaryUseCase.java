package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.BacklogSummaryInfo;
import com.orbit.schedule.application.port.in.query.dto.GetBacklogSummaryQuery;

public interface GetBacklogSummaryUseCase {

    BacklogSummaryInfo get(GetBacklogSummaryQuery query);
}
