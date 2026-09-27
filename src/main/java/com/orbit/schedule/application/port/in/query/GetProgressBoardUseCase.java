package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;
import com.orbit.schedule.application.port.in.query.dto.ProgressBoardInfo;

public interface GetProgressBoardUseCase {

    ProgressBoardInfo get(GetProgressBoardQuery query);
}
