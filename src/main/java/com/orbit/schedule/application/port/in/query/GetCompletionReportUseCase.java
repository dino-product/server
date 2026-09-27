package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkCompletionReportInfo;

public interface GetCompletionReportUseCase {

    WorkCompletionReportInfo get(GetCompletionReportQuery query);
}
