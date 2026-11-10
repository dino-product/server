package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;

public interface ParticipationRequestQueryPort {

    /** 계정이 보낸 모든 참여 요청을 상태와 관계없이 요청 일시순(같으면 식별자순)으로 읽는다. */
    List<AccountParticipationRequest> listByAccount(AccountId accountId);

    /** 발주사의 승인 대기 요청을 요청 일시가 오래된 순(같으면 식별자순)으로 읽는다(순서는 O-19). */
    List<ParticipationRequest> listPending(OrganizationId organizationId);

    /** 거절된 요청의 거절 사유. 사유 없이 거절됐거나 거절되지 않은 요청, 없는 요청이면 빈 값이다. */
    Optional<String> findRejectReason(ParticipationRequestId id);
}
