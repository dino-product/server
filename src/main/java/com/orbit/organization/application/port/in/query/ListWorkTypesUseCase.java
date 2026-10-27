package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ListWorkTypesQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;

public interface ListWorkTypesUseCase {

    List<WorkTypeInfo> list(ListWorkTypesQuery query);
}
