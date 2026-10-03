package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;
import com.orbit.schedule.application.port.in.query.dto.TimetableInfo;

public interface GetTimetableUseCase {

    TimetableInfo get(GetTimetableQuery query);
}
