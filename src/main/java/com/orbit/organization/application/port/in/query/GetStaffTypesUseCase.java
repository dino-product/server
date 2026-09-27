package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.GetStaffTypesQuery;
import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;

public interface GetStaffTypesUseCase {
    List<StaffTypeInfo> get(GetStaffTypesQuery query);
}
