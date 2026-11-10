package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;

public interface GetPendingWorksUseCase {

    MyScheduleInfo get(GetTechnicianWorksQuery query);
}
