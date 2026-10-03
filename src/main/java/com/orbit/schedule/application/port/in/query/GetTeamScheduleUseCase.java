package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetTeamScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.TeamScheduleInfo;

public interface GetTeamScheduleUseCase {

    TeamScheduleInfo get(GetTeamScheduleQuery query);
}
