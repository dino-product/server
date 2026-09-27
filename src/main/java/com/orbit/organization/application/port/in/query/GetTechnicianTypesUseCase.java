package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.GetTechnicianTypesQuery;
import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;

public interface GetTechnicianTypesUseCase {
    List<TechnicianTypeInfo> get(GetTechnicianTypesQuery query);
}
