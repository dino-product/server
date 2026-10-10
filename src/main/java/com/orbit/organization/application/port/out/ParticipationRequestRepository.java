package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;

public interface ParticipationRequestRepository {

    ParticipationRequest save(ParticipationRequest request);

    /** 계정이 그 발주사에 보낸 승인 대기 요청. 같은 계정·발주사의 승인 대기 요청은 하나뿐이다. */
    Optional<ParticipationRequest> findPending(OrganizationId organizationId, AccountId accountId);
}
