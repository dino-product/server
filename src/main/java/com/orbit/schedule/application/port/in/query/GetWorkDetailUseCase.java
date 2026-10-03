package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;

public interface GetWorkDetailUseCase {

    WorkDetailInfo get(GetWorkDetailQuery query);
}
