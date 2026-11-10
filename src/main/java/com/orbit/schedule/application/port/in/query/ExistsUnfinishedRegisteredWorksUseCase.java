package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.UnfinishedRegisteredWorksQuery;

public interface ExistsUnfinishedRegisteredWorksUseCase {

    boolean exists(UnfinishedRegisteredWorksQuery query);
}
