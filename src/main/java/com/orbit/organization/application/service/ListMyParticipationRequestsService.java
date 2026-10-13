package com.orbit.organization.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListMyParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMyParticipationRequestsQuery;
import com.orbit.organization.application.port.in.query.dto.MyParticipationRequestInfo;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;

/** 신청 현황은 요청자가 보낸 모든 참여 요청(승인 대기·승인·거절·취소)을 요청 일시순으로 보인다(O-16). */
@Service
public class ListMyParticipationRequestsService implements ListMyParticipationRequestsUseCase {

    private final ParticipationRequestQueryPort queryPort;

    public ListMyParticipationRequestsService(ParticipationRequestQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyParticipationRequestInfo> list(ListMyParticipationRequestsQuery query) {
        AccountId requester = new AccountId(Objects.requireNonNull(query.accountId(), "accountId must not be null"));
        return queryPort.listByAccount(requester).stream()
                .map(MyParticipationRequestInfo::from)
                .toList();
    }
}
