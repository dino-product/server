package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.PendingParticipationRequestInfo;

public interface ListPendingParticipationRequestsUseCase {

    List<PendingParticipationRequestInfo> list(ListPendingParticipationRequestsQuery query);
}
