package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;

public interface GetMyScheduleUseCase {

    MyScheduleInfo get(GetMyScheduleQuery query);
}
