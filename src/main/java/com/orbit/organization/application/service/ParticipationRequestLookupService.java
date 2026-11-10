package com.orbit.organization.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.ParticipationRequestLookup;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.ParticipationRequestId;

@Service
public class ParticipationRequestLookupService implements ParticipationRequestLookup {

    private final ParticipationRequestQueryPort queryPort;

    public ParticipationRequestLookupService(ParticipationRequestQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findRejectReason(Long requestId) {
        return queryPort.findRejectReason(new ParticipationRequestId(requestId));
    }
}
