package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;

public interface SearchWorksUseCase {

    WorkSearchInfo search(SearchWorksQuery query);
}
