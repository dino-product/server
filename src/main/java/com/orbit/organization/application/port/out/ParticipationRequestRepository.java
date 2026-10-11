package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;

public interface ParticipationRequestRepository {

    ParticipationRequest save(ParticipationRequest request);

    /**
     * 요청 행을 트랜잭션 끝까지 잠그고 읽는다. 같은 요청을 바꾸는 취소와 승인·거절(HM-290)이 한 줄로 서서, 앞 요청이 커밋한 상태를 보고 판정한다. 같은 트랜잭션에서
     * 이 요청을 먼저 읽지 않는다(먼저 읽으면 잠근 뒤에도 그때 읽은 상태를 돌려줄 수 있다).
     */
    Optional<ParticipationRequest> findByIdForUpdate(ParticipationRequestId id);

    /** 계정이 그 발주사에 보낸 승인 대기 요청. 같은 계정·발주사의 승인 대기 요청은 하나뿐이다. */
    Optional<ParticipationRequest> findPending(OrganizationId organizationId, AccountId accountId);
}
