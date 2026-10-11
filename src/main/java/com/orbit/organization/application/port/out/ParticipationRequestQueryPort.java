package com.orbit.organization.application.port.out;

import java.util.List;

import com.orbit.organization.domain.AccountId;

public interface ParticipationRequestQueryPort {

    /** 계정이 보낸 모든 참여 요청을 상태와 관계없이 요청 일시순(같으면 식별자순)으로 읽는다. */
    List<AccountParticipationRequest> listByAccount(AccountId accountId);
}
