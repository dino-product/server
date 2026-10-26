package com.orbit.organization.application.port.in.query;

import com.orbit.organization.application.port.in.query.dto.ListParticipantsQuery;
import com.orbit.organization.application.port.in.query.dto.ParticipantsInfo;

public interface ListParticipantsUseCase {

    ParticipantsInfo list(ListParticipantsQuery query);
}
