package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ListMyParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.MyParticipationRequestInfo;

public interface ListMyParticipationRequestsUseCase {

    List<MyParticipationRequestInfo> list(ListMyParticipationRequestsQuery query);
}
